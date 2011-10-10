(ns circle.backend.ec2
  (:use [circle.aws-credentials :only (aws-credentials)])
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
   (map bean)))
  
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

