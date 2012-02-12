(ns circle.backend.action.nodes
  (:require [circle.backend.nodes :as nodes])
  (:require [circle.backend.ec2 :as ec2])
  (:use [circle.util.core :only (sha1)])
  (:use [circle.backend.action :only (defaction add-action-result)])
  (:require [circle.backend.ssh :as ssh])
  (:use [clojure.tools.logging :only (infof errorf)]))

(defn ensure-ssh-keys
  "If the node does not contain :private-key, generate a new set of keys and add them to the node"
  [node]
  (if (not (:private-key node))
    (merge node (ssh/generate-keys))
    node))

(defn ensure-keypair
  "If the node does not contain keypair-name, upload the keys to EC2. Node must already have private keys"
  [node]
  {:pre [(-> node :public-key)]}
  (if (not (:keypair-name node))
    (let [keypair-name (str "keypair-" (-> node :public-key (sha1)))]
      (ec2/import-keypair keypair-name (:public-key node))
      (merge {:keypair-name keypair-name} node))
    node))

(defn ensure-keys
  "Given a build, if the build's node doesn't contain SSH keys, generate keys and add them."
  [b]
  (let [node (-> @b :node (ensure-ssh-keys) (ensure-keypair))]
    (dosync
     (alter b assoc :node node))))

(defaction start-nodes []
  {:name "start nodes"
   :type :infrastructure}
  (fn [build]
    (ensure-keys build)
    (let [instance-ids (ec2/start-instances-retry (-> @build :node))
          group (-> @build :group)]
      (when group
        (nodes/configure instance-ids))
      (dosync
       (alter build assoc :instance-ids instance-ids)))))

(defn cleanup-nodes
  "terminates nodes, and kills keypairs. forcibly terminate the node."
  [build]
  (let [ids (-> @build :instance-ids)]
    (when (seq ids)
      (infof "terminating nodes %s" ids)
      (apply ec2/safe-terminate ids))
    (when (-> @build :node :keypair-name)
      (infof "deleting keypair %s" (-> @build :node :keypair-name))
      (ec2/delete-keypair (-> @build :node :keypair-name)))))

(defaction stop-nodes []
  {:name "stop nodes"
   :type :infrastructure}
  (fn [build]
    (cleanup-nodes build)))

