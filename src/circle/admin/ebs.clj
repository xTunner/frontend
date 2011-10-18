(ns circle.admin.ebs
  (:require [org.jclouds.ec2.ebs2 :as ebs])
  (:require [circle.backend.ec2 :as ec2])
  (:use [circle.utils.except :only (throw-if-not)])
  (:use [circle.aws-credentials :only (jclouds-compute)]))

(defn default-volume [instance-id]
  (let [inst (ec2/instance instance-id)
        devices (map bean (-> inst :blockDeviceMappings))
        root-dev (-> inst :rootDeviceName)
        dev (first (filter #(not= root-dev (:deviceName %)) devices))]
    (-> dev :ebs (bean) :volumeId)))

(defn volume-from-id
  "returns a Volume from the id"
  [vol-id]
  (->>
   jclouds-compute
   (ebs/volumes)
   (filter #(= vol-id (.getId %)))
   (first)))

(defn take-snapshot
  "Create a snapshot of an EBS volume.
  ec2-id - and AWS instance id
  description - 

  Options:
  vol-id - the vol-id. If not specified, uses the first non-root device.
   "
  [ec2-id description & {:keys [vol-id]}]
  (ebs/create-snapshot jclouds-compute (volume-from-id (or vol-id (default-volume ec2-id))) description))

(defn latest-snapshot
  "Returns the snapshot-id with matching description"
  [description]
  (->> 
   (ebs/snapshots jclouds-compute :owner "self")
   (map bean)
   (filter #(= description (:description %)))
   (sort-by :startTime)
   (last)
   :id))