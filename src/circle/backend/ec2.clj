(ns circle.backend.ec2
  (:use [circle.aws-credentials :only (aws-credentials)])
  (:import com.amazonaws.services.ec2.AmazonEC2Client
           (com.amazonaws.services.ec2.model DescribeInstancesResult
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
  []
  (with-ec2-client client
    (-> client
        (.describeInstances)
        (.getReservations)
        (->> (map bean)))))

(defn all-instance-ids []
  (->>
   (reservations)
   (mapcat :instances)
   (map bean)
   (map :instanceId)))

(defn terminate-instances [instance-ids]
  (with-ec2-client client
    (-> client
        (.terminateInstances (TerminateInstancesRequest. instance-ids))
        (bean))))

(defn terminate-all-instances []
  (terminate-instances (all-instance-ids)))