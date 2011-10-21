(ns circle.backend.ec2
  (:use [circle.aws-credentials :only (aws-credentials)])
  (:use [clojure.tools.logging :only (infof)])
  (:use [clojure.core.incubator :only (-?>)])
  (:import com.amazonaws.services.ec2.AmazonEC2Client
           (com.amazonaws.services.ec2.model DescribeInstancesResult
                                             TerminateInstancesRequest
                                             DeleteSecurityGroupRequest
                                             DeleteKeyPairRequest)))

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
  []
  (with-ec2-client client
    (-> client
        (.describeInstances)
        (.getReservations)
        (->> (map bean)))))

(defn instances []
  (->>
   (reservations)
   (mapcat :instances)
   (map bean)
   (filter #(not= :terminated (-?> % :state (bean) :name (keyword))))))
  
(defn all-instance-ids []
  (map :instanceId (instances)))

(defn instance [instance-id]
  (first (filter #(= instance-id (:instanceId %)) (instances))))

(defn get-availability-zone [instance-id]
  (-> instance-id
      (instance)
      :placement
      (.getAvailabilityZone)))

(defn terminate-instances [instance-ids]
  (with-ec2-client client
    (-> client
        (.terminateInstances (TerminateInstancesRequest. instance-ids))
        (bean))))

(defn terminate-all-instances []
  (terminate-instances (all-instance-ids)))

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
    (println "deleting" group)
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
  (println "deleting keypair" name)
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