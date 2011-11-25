(ns circle.backend.action.nodes
  (:require [circle.backend.nodes :as nodes])
  (:require [circle.backend.ec2 :as ec2])
  (:use [circle.backend.action :only (defaction add-action-result)])
  (:require [circle.backend.ssh :as ssh])
  (:use [clojure.tools.logging :only (errorf)]))

(defn ensure-keys
  "Given a build, if the build's node doesn't contain SSH keys, generate keys and add them."
  [b]
  (dosync
   (when (not (-> @b :node :private-key))
     (let [keys (ssh/generate-keys)
           keypair-name (ec2/ensure-keypair "temp" (-> keys :public-key))]
       (-> b
           (alter assoc-in [:node :private-key] (-> keys :private-key))
           (alter assoc-in [:node :public-key] (-> keys :public-key))
           (alter assoc-in [:node :keypair-name] keypair-name))))))

(defaction start-nodes []
  {:name "start nodes"}
  (fn [build]
    (let [instance-ids (ec2/start-instances (-> @build :node))
          group (-> @build :group)]
      (when group
        (nodes/configure-instance-ids instance-ids))
      (dosync
       (alter build assoc :instance-ids instance-ids)))))

(defaction stop-nodes []
  {:name "stop nodes"}
  (fn [build]
    (apply ec2/terminate-instances! (-> @build :instance-ids))))

(defn cleanup-nodes [build]
  (let [ids (-> @build :instance-ids)]
    (when (seq ids)
      (errorf "terminating nodes %s" ids)
      (apply ec2/terminate-instances! ids))))