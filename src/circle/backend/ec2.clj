(ns circle.backend.ec2
  (:require [clojure.string :as str])
  (:require [clj-http.client :as http])
  (:use [circle.aws-credentials :only (aws-credentials)])
  (:use [circle.util.core :only (apply-map sha1)]
        [circle.util.except :only (throwf throw-if-not)]
        [circle.util.args :only (require-args)])
  (:use [clojure.tools.logging :only (infof error errorf)])
  (:use [robert.bruce :only (try-try-again)])
  (:use [clojure.core.incubator :only (-?>)])
  (:use [arohner.utils :only (inspect)])
  (:use [doric.core :only (table)])
  (:require [circle.env :as env])
  (:require [circle.backend.ssh])
  (:require [circle.backend.load-balancer :as lb])
  (:import com.amazonaws.services.ec2.AmazonEC2Client
           com.amazonaws.AmazonClientException
           com.amazonaws.AmazonServiceException
           (com.amazonaws.services.ec2.model CreateImageRequest
                                             DeleteKeyPairRequest
                                             DeleteSecurityGroupRequest
                                             DescribeImagesRequest
                                             DescribeInstancesRequest
                                             DescribeInstancesResult
                                             DescribeKeyPairsRequest
                                             ImportKeyPairRequest
                                             Tag
                                             CreateTagsRequest
                                             Placement
                                             RunInstancesRequest
                                             TerminateInstancesRequest
                                             DescribeInstanceAttributeRequest
                                             ModifyInstanceAttributeRequest)))

;; http://docs.amazonwebservices.com/AWSEC2/latest/UserGuide/AESDG-chapter-instancedata.html?r=1890
(def aws-metadata-url "http://169.254.169.254/latest/meta-data/")

(defn self-metadata
  "Returns metadata about the current instance. attr is a string/keyword"
  [attr]
  (try
    (let [resp (http/get (format "%s/%s" aws-metadata-url attr) {:throw-exceptions false
                                                                 :socket-timeout 1000
                                                                 :conn-timeout 1000})]
      (when (= 200 (-> resp :status))
        (-> resp :body)))
    (catch Exception e
      nil)))

(defn self-instance-id
  "If the local box is an EC2 instance, returns the instance id, else nil."
  []
  (self-metadata "instance-id"))

(defmacro with-ec2-client
  [client & body]
  `(let [~client (AmazonEC2Client. aws-credentials)]
     (try-try-again
      {:sleep 1000
       :tries 30
       :catch [AmazonClientException]
       :error-hook (fn [e#]
                     (errorf "caught %s %s" (class e#) e#)
                     ;; We don't want to catch (many) ServiceExceptions, because they can often be programming errors.
                     (if (= (class e#) com.amazonaws.AmazonServiceException)
                       false
                       nil))}
      #(do
         ~@body))))

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

(defn raw-instances
  "Returns a seq of one map per instance. If instance-ids are passed,
  will only return maps for those instances"
  [& instance-ids]
  (->>
   (apply reservations instance-ids)
   (mapcat :instances)
   (map bean)
   (map #(update-in % [:state] bean))))

(defn instances
  "same as raw-instances, but filters out non-running instances"
  [& instance-ids]
  (->> (apply raw-instances instance-ids)
       (filter #(not= :terminated (-?> % :state :name (keyword))))))

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
  [& instance-ids]
  (when (seq instance-ids)
    (with-ec2-client client
      (-> client
          (.terminateInstances (TerminateInstancesRequest. instance-ids))
          (bean)))))

(def production-load-balancer "www")

(defn safe-terminate
  "Terminates instances that are not attached to the load balancer."
  [& instance-ids]
  (let [safe-instances (into #{} (lb/instances production-load-balancer))
        kill-instances (remove #(contains? safe-instances %) instance-ids)]
    (when (seq kill-instances)
      (apply terminate-instances! kill-instances))))

(defn tagmap
  "Given an inst returned by instances, return the tags as a single map"
  [inst]
  (into {} (for [t (map bean (-> inst :tags))]
             [(keyword (:key t)) (:value t)])))

(defn my-instance?
  "Given an instance returned by (instances) or (instance), return true if this username and hostname started the instance"
  [inst]
  (let [tags (tagmap inst)]
    (and (= (env/hostname) (-> tags :hostname))
         (= (env/username) (-> tags :username)))))

(defn terminate-my-instances
  "Terminate all instances with the same username and hostname tags as the current user"
  []
  (->>
   (instances)
   (filter my-instance?)
   (map :instanceId)
   (apply terminate-instances!)))

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
  started. Returns the keypair name"
  [name pub-key]
  (try
    (with-ec2-client client
      (-> client
          (.importKeyPair (ImportKeyPairRequest. name pub-key))
          (bean)))
    (catch AmazonServiceException e
      (when (not= "InvalidKeyPair.Duplicate" (.getErrorCode e))
        (throw e)))))

(defn describe-keypairs
  "With no arguments, returns all keypairs. When passed names of keys
  returns information about only those keys. Returns nil if a
  specified key can't be found"
  [& names]
  (with-ec2-client client
    (try
      (let [request (DescribeKeyPairsRequest.)]
        (when (seq names)
          (.withKeyNames request names))
        (-> client
            (.describeKeyPairs request)
            (->>
             (.getKeyPairs)
             (map bean))))
      (catch AmazonServiceException e
        (when (not= "InvalidKeyPair.NotFound" (-> e (bean) :errorCode))
          (throw e))))))

(defn ensure-keypair
  "Makes sure the key is uploaded to AWS. Returns the keypair name."
  [name pub-key]
  (let [key-name (str name "-" (sha1 pub-key))]
    (when-not (describe-keypairs key-name)
      (import-keypair key-name pub-key))
    key-name))

(defn describe-image [ami]
  (with-ec2-client client
    (-> client
        (.describeImages (-> (DescribeImagesRequest.) (.withImageIds [ami])))
        (.getImages)
        (first)
        (bean))))

(defn image-state
  "Given an ami, return the state of the image, a keyword, like :pending or :available"
  [ami]
  (-> (describe-image ami)
      :state
      (keyword)))

(defn add-tags
  "Adds tags to instances. instance-ids is a seq of strings, each an
  instance-id (or ID of something that can be tagged). tags is a map."
  [instance-ids tags]
  (with-ec2-client client
    (let [request (CreateTagsRequest. instance-ids (for [[k v] tags]
                                                     (Tag. (name k) (name v))))]
      (.createTags client request))))

(defn describe-tags
  "returns all tags on all instances"
  ([]
     (with-ec2-client client
       (let [result (.describeTags client)]
         (map bean (.getTags result))))))

(defn block-until-running
  "Blocks until AWS claims the instance is running"
  [instance-id & {:keys [timeout sleep-interval]
                  :or {timeout 30
                       sleep-interval 10}}]
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
          ip (-> inst :publicIpAddress)]
      (infof "block-until-running: %s %s" instance-id state)
      (cond
       (= state :running) true
       (pos? timeout) (do (Thread/sleep (* sleep-interval 1000)) (recur (- timeout sleep-interval)))
       :else (try
               (terminate-instances! instance-id)
               (finally
                (throwf "instance %s didn't start within timeout" instance-id)))))))

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
       :else (do
               (terminate-instances! instance-id)
               (throwf "failed to SSH into %s" instance-id))))))

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
         max-count 1}
    :as args}]
  (require-args ami availability-zone instance-type keypair-name security-groups)
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
    (add-tags instance-ids {:username (env/username)
                            :hostname (or (self-instance-id) (env/hostname))
                            :timestamp (str (java.util.Date.))})
    instance-ids))

(defn print-instances []
  (->> (instances)
       (map (fn [inst]
              (-> inst
               (assoc :tags (into (sorted-map) (for [t (map bean (-> inst :tags))] [(:key t) (:value t)]))))))
       (table [:instanceId :publicIpAddress :imageId :tags])
       (println)))

(defn image-wait-for-ready [image-name]
  (try-try-again
   {:sleep 15000
    :tries (* 4 10)}
   #(throw-if-not (= :available (image-state image-name)) "AMI did not become available in timeout window")))

(defn create-image
  "Create an AMI from a running instance. Returns the new AMI-id. Blocks until the image is available."
  [instance-id image-name]
  (with-ec2-client client
    (let [ami (-> client
                  (.createImage (CreateImageRequest. instance-id image-name))
                  (.getImageId))]
      (println image-name "=>" ami)
      (image-wait-for-ready ami)
      ami)))

(defn get-instance-attr [instance-id attr]
  (with-ec2-client client
    (-> client
        (.describeInstanceAttribute (DescribeInstanceAttributeRequest. instance-id attr))
        (.getInstanceAttribute)
        (bean)
        (get (keyword attr)))))

(defn set-shutdown-behavior
  "Specifies what the instance will do when 'shutdown' is run locally
  on the instance. value can be the string or keyword 'stop' (ec2
  instance is no longer running, but we still pay for the EBS volume)
  or 'terminate' (also poweroff EBS)"
  [instance-id value]
  (with-ec2-client client
    (let [request (doto (ModifyInstanceAttributeRequest.)
                    (.setInstanceId instance-id)
                    (.setInstanceInitiatedShutdownBehavior (name value)))]
      (.modifyInstanceAttribute client request))))