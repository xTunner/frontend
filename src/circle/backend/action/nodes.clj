(ns circle.backend.action.nodes
  (:require [circle.backend.nodes :as nodes])
  (:require [circle.backend.ec2 :as ec2])
  (:use [circle.backend.action :only (defaction add-action-result)])
  (:use [clojure.tools.logging :only (errorf)]))

(defaction start-nodes []
  {:name "start nodes"}
  (fn [build]
    (let [group (-> @build :group)
          instance-ids (nodes/start-and-configure group)
          compute (nodes/pallet-compute-service group (map ec2/public-ip instance-ids))]
      (dosync
       (alter build assoc :instance-ids instance-ids)
       (alter build assoc :pallet-compute compute)
       (alter build assoc :node-info (nodes/node-info group :compute compute))))))

(defaction stop-nodes []
  {:name "stop nodes"}
  (fn [build]
    (apply ec2/terminate-instances! (-> @build :instance-ids))))

(defn cleanup-nodes [build]
  (let [ids (-> @build :instance-ids)]
    (when (seq ids)
      (errorf "terminating nodes %s" ids)
      (apply ec2/terminate-instances! ids))))