(ns circle.backend.ec2
  (:require [clojure.string :as str])
  (:use [circle.aws-credentials :only (aws-credentials)])
  (:use [circle.utils.core :only (apply-map)]
        [circle.utils.except :only (throwf)]
        [circle.utils.args :only (require-args)])
  (:use [clojure.tools.logging :only (infof error)])
  (:use [clojure.core.incubator :only (-?>)])
  (:use [arohner.utils :only (inspect)])
  (:use [doric.core :only (table)])
  (:require [circle.backend.ssh])
  (:import com.amazonaws.services.ec2.AmazonEC2Client
           com.amazonaws.AmazonServiceException
           (com.amazonaws.services.ec2.model DeleteKeyPairRequest
                                             DeleteSecurityGroupRequest
                                             DescribeImagesRequest
                                             DescribeInstancesRequest
                                             DescribeInstancesResult
                                             ImportKeyPairRequest
                                             Placement
                                             RunInstancesRequest
                                             TerminateInstancesRequest)))

(defmacro with-ec2-client
  [client & body]
  `(let [~client (AmazonEC2Client. aws-credentials)]
     ~@body))

(defn availability-zones []
  (-> (AmazonEC2Client. aws-credentials)
      (.describeAvailabilityZones)
      (.getAvailabilityZones)
      (->>
       (map (fn [az]
              {:zone (.getZoneName az)
               :region (.getRegionName az)
               :status (.getState az)})))))

(defn reservations
  "returns all ec2 reservations"
  [& instance-ids]
  (with-ec2-client client
    (let [reservations (if (seq instance-ids)
                         (-> client (.describeInstances (-> (DescribeInstancesRequest.)
                                                            (.withInstanceIds instance-ids))))
                         (-> client (.describeInstances)))]
      (-> reservations
          (.getReservations)
          (->> (map bean))))))

(defn instances
  "Returns a seq of one map per instance. If instance-ids are passed, will only return maps for those instances"
  [& instance-ids]
  (->>
   (apply reservations instance-ids)
   (mapcat :instances)
   (map bean)
   (filter #(not= :terminated (-?> % :state (bean) :name (keyword))))
   (map #(update-in % [:state] bean))))
  
(defn all-instance-ids []
  (map :instanceId (instances)))

(defn instance [instance-id]
  (first (instances instance-id)))

(defn get-availability-zone [instance-id]
  (-> instance-id
      (instance)
      :placement
      (.getAvailabilityZone)))

(defn public-ip [instance-id]
  (-> (instance instance-id) :publicIpAddress))

(defn terminate-instances!
  [instance-ids]
  (when (seq instance-ids)
    (with-ec2-client client
      (-> client
          (.terminateInstances (TerminateInstancesRequest. instance-ids))
          (bean)))))

(defn terminate-all-instances! []
  (terminate-instances! (all-instance-ids)))

(defn security-groups
  []
  (with-ec2-client client
    (-> client
        (.describeSecurityGroups)
        (.getSecurityGroups)
        (->>
         (map bean)))))

(defn delete-group [group-name]
  (with-ec2-client client
    (-> client
        (.deleteSecurityGroup (DeleteSecurityGroupRequest. group-name)))))

(defn delete-groups-matching
  "delete all ec2 security groups matching the regex. Mainly used as repl workaround for jclouds bugs"
  [regex]
  (doseq [group (filter #(re-find regex %) (map :groupName (security-groups)))]
    (infof "deleting %s" group)
    (delete-group group)))

(defn keypairs []
  (with-ec2-client client
    (-> client
        (.describeKeyPairs)
        (.getKeyPairs)
        (->>
         (map bean)))))

(defn delete-keypair
  [name]
  (infof "deleting keypair %s" name)
  (with-ec2-client client
    (-> client
        (.deleteKeyPair (DeleteKeyPairRequest. name)))))

(defn delete-keypairs-matching [re]
  (doseq [kp (filter #(re-find re (:keyName %)) (keypairs))]
    (delete-keypair (-> kp :keyName))))

(defn delete-unused-jclouds-keypairs
  []
  (let [keep (into #{} (map :keyName (instances)))]
    (doseq [k (->> (keypairs)
                   (filter #(re-find #"^jclouds" (:keyName %)))
                   (filter #(not (contains? keep (:keyName %)))))]
      (delete-keypair (:keyName k)))))

(defn import-keypair
  "Uploads an SSH public key to AWS. When starting nodes, name will be
  passed to AWS. Use the private key to log into boxes after
  started."
  [name pub-key]
  (try
    (with-ec2-client client
      (-> client
          (.importKeyPair (ImportKeyPairRequest. name pub-key))))
    (catch AmazonServiceException e
      (when (not= "InvalidKeyPair.Duplicate" (.getErrorCode e))
        (throw e)))))

(defn describe-image [ami]
  (with-ec2-client client
    (-> client
        (.describeImages (-> (DescribeImagesRequest.) (.withImageIds [ami]))))))

(defn block-until-running
  "Blocks until AWS claims the instance is running"
  [instance-id & {:keys [timeout]
                  :or {timeout 300}}]
    (infof "block-until-running: waiting for instance %s to start" instance-id)
  (loop [timeout timeout]
    (let [inst (try
                 (instance instance-id)
                 (catch com.amazonaws.AmazonServiceException e
                   ;; this is an eventual consistency 'race'. Sometimes AWS
                   ;; reports the instance is not there right after it's
                   ;; started.
                   (when (not= "InvalidInstanceID.NotFound" (.getErrorCode e)) 
                     (throw e))))
          state (-?> inst :state :name (keyword))
          ip (-> inst :publicIpAddress)
          sleep-interval 5]
      (infof "block-until-running: %s %s" instance-id state)
      (cond
       (= state :running) true
       (pos? timeout) (do (Thread/sleep (* sleep-interval 1000)) (recur (- timeout sleep-interval)))
       :else (throwf "instance %s didn't start within timeout" instance-id)))))

(defn block-until-ready
  "Block until we can successfully SSH into the box."
  [instance-id & {:keys [timeout username public-key private-key]
                  :or {timeout 60}
                  :as args}]
  (require-args instance-id username public-key private-key)
  (block-until-running instance-id)
  (infof "waiting for instance %s to be ready for SSH" instance-id)
  (let [success (atom false)
        sleep-interval 5]
    (loop [timeout timeout]
      (try
        (let [node {:ip-addr (public-ip instance-id) :username username :public-key public-key :private-key private-key}
              resp (circle.backend.ssh/remote-exec node "echo 'hello'")]
          (when (= 0 (-> resp :exit))
            (swap! success (constantly true))))
        (catch java.net.ConnectException e
          (infof "block-until-ready: caught %s" (.getMessage e)))
        (catch com.jcraft.jsch.JSchException e
          (infof "block-until-ready: caught %s" (.getMessage e))))
      (cond
       @success true
       (pos? timeout) (do (Thread/sleep (* sleep-interval 1000)) (recur (- timeout sleep-interval)))
       :else (throwf "failed to SSH into %s" instance-id)))))

(defn start-instances*
  "Starts one or more instances. Returns a seq of instance-ids."
  [{:keys [ami
           keypair-name
           security-groups
           instance-type
           min-count ;; min number of instances to start
           max-count 
           availability-zone]
    :or {min-count 1
         max-count 1}}]
  (with-ec2-client client
    (-> client
        (.runInstances (->
                        (RunInstancesRequest.)
                        (.withImageId ami)
                        (.withPlacement (Placement. availability-zone))
                        (.withInstanceType instance-type)
                        (.withKeyName keypair-name)
                        (.withMinCount min-count)
                        (.withMaxCount max-count)
                        (.withSecurityGroups security-groups)))
        (.getReservation)
        (.getInstances)
        (->>
         (map bean)
         (map :instanceId)))))

(defn start-instances
  "Takes a map with all keys described in start-instances*, username,
  public-key, private-key. Blocks until we can can successfully
  SSH in (or timeout). Returns a seq of instance-ids "
  [{:keys [timeout username public-key private-key]
    :or {timeout 60}
    :as args}]
  (let [instance-ids (start-instances* args)]
    (every? #(block-until-ready %
                                :timeout timeout
                                :username username
                                :public-key public-key
                                :private-key private-key) instance-ids)
    instance-ids))

(defn print-instances []
  (->> (instances)
       (map (fn [inst]
              (-> inst
               (assoc :state-name (-> inst :state :name))
               (assoc :security-groups (str/join "," (for [g (:securityGroups inst)]
                                                       (.getGroupName g))))
               (assoc :tags (into {} (for [t (map bean (-> inst :tags))] [(:key t) (:value t)]))))))
       (table [:instanceId :state-name :publicIpAddress :imageId :security-groups :tags])
       (println)))