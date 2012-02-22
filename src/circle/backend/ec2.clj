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
  (:use [doric.core :only (table)])
  (:require [circle.env :as env])
  (:require [circle.backend.ssh])
  (:require [circle.backend.load-balancer :as lb])
  (:require [clj-time.core :as time])
  (:use [circle.util.retry :only (wait-for)])
  (:import com.amazonaws.services.ec2.AmazonEC2Client
           com.amazonaws.AmazonClientException
           com.amazonaws.AmazonServiceException
           (com.amazonaws.services.ec2.model AttachVolumeRequest
                                             CreateImageRequest
                                             CreateVolumeRequest
                                             DeleteKeyPairRequest
                                             DeleteSecurityGroupRequest
                                             DescribeImagesRequest
                                             DescribeInstancesRequest
                                             DescribeInstancesResult
                                             DescribeKeyPairsRequest
                                             DescribeTagsRequest
                                             DescribeVolumesRequest
                                             DetachVolumeRequest
                                             Filter
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

(def instance-limit 100) ;; instance limit for our account. As of 2012/02/06, there is no API to determine your instance limit.

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
     (wait-for
      {:sleep (time/secs 1)
       :timeout (time/secs 30)
       :catch [AmazonClientException]
       :success-fn :no-throw
       :error-hook (fn [e#]
                     (errorf e# "with-ec2-client: caught %s" e#)
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

(defn tagmap
  "Given an inst returned by instances, return the tags as a single map"
  [inst]
  (into {} (for [t (map bean (-> inst :tags))]
             [(keyword (:key t)) (:value t)])))

(defn raw-instances
  "Returns a seq of one map per instance. If instance-ids are passed,
  will only return maps for those instances"
  [& instance-ids]
  (->>
   (apply reservations instance-ids)
   (mapcat :instances)
   (map bean)
   (map #(update-in % [:state] bean))
   (map #(assoc % :tags (tagmap %)))))

(defn instances
  "same as raw-instances, but filters out non-running instances"
  [& instance-ids]
  (->> (apply raw-instances instance-ids)
       (filter #(not= :terminated (-?> % :state :name (keyword))))))

(defn all-instance-ids []
  (map :instanceId (instances)))

(defn instance [instance-id]
  (first (raw-instances instance-id)))

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
    (infof "terminating %s" (str/join ", " instance-ids))
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
  ([& {:keys [instance-id]}]
     (with-ec2-client client
       (let [request (DescribeTagsRequest.)
             _ (when instance-id
                 (.withFilters request [(Filter. "resource-id" [instance-id])]))
             result (.describeTags client request)]
         (map bean (.getTags result))))))

(defn block-until-running
  "Blocks until AWS claims the instance is running"
  [instance-id & {:keys [timeout sleep-interval]
                  :or {timeout (time/secs 30)
                       sleep-interval (time/secs 10)}}]
  (infof "block-until-running: waiting for instance %s to start" instance-id)
  (infof "block-until-running: sleep-interval: %s %s" sleep-interval timeout)
  (try
    (wait-for
     {:sleep sleep-interval
      :timeout timeout
      :error-hook (fn [e] (infof "block-until-running: caught %s %s" (class e) (.getMessage e)))
      :success-fn (fn [inst] (inspect (= :running (-?> inst :state :name (keyword)))))}
     #(instance instance-id))
    (catch Exception e
      (errorf "instance %s didn't start within timeout" instance-id)
      (terminate-instances! instance-id)
      (throw e))))

(defn block-until-ready
  "Block until we can successfully SSH into the box."
  [instance-id & {:keys [timeout username public-key private-key]
                  :or {timeout 60}
                  :as args}]
  (require-args instance-id username public-key private-key)
  (block-until-running instance-id)
  (infof "waiting for instance %s to be ready for SSH" instance-id)
  (try
    (wait-for
     {:sleep (time/secs 10)
      :timeout (time/secs 90)
      :catch [java.net.ConnectException com.jcraft.jsch.JSchException]
      :error-hook (fn [e] (infof "block-until-ready: caught %s" (.getMessage e)))}
     (fn []
       (let [node {:ip-addr (public-ip instance-id) :username username :public-key public-key :private-key private-key}]
         (circle.backend.ssh/remote-exec node "echo 'hello'"))))
    (catch Exception e
      (errorf "failed to SSH into %s" instance-id)
      (terminate-instances! instance-id)
      (throw e))))

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
                        (.withMinCount (Integer. min-count))
                        (.withMaxCount (Integer. max-count))
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

(defn start-instances-retry
  "EC2 is unreliable, and occasionally gives us broken boxes. Retry
  until we get a good one."
  [args]
  (wait-for
   {:sleep nil
    :tries 3
    :catch [Exception]}
   #(start-instances args)))

(defn print-instances []
  (->> (instances)
       (map (fn [inst]
              (-> inst
               (assoc :tags (into (sorted-map) (for [t (map bean (-> inst :tags))] [(:key t) (:value t)]))))))
       (table [:instanceId :publicIpAddress :imageId :tags])
       (println)))

(defn image-wait-for-ready [image-name]
  (wait-for
   {:sleep (time/secs 15)
    :timeout (time/minutes 15)}
   #(= :available (image-state image-name))))

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

(defn create-volume
  "Create a new EBS volume. zone is required. One of snapshot-id or size is required"
  [& {:keys [snapshot-id availability-zone size]}]
  (with-ec2-client client
    (let [request (CreateVolumeRequest.)]
      (.setAvailabilityZone request availability-zone)
      (when size
        (.setSize request (Integer. size)))
      (when snapshot-id
        (.setSnapshotId request snapshot-id))
      (-> (.createVolume client request)
          (.getVolume)
          (bean)))))

(defn describe-volumes [volume-ids]
  (with-ec2-client client
    (-> client
        (.describeVolumes (DescribeVolumesRequest. volume-ids)))))

(defn attach-volume
  "Attach a volume to an instance"
  ;; Currently (2012/02/15) AWS lies to you. You're required to
  ;; specify a device as /dev/sdb1, but then it appears as /dev/xvdb1
  [volume-id instance-id device]
  (with-ec2-client client
    (-> client
        (.attachVolume (AttachVolumeRequest. volume-id instance-id device)))))

(defn detach-volume [volume-id instance-id]
  (with-ec2-client client
    (-> client
        (.detachVolume (doto (DetachVolumeRequest. volume-id)
                         (.setInstanceId instance-id))))))
