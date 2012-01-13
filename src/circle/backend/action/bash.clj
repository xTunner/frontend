(ns circle.backend.action.bash
  "functions for running bash on remote instances, and build actions for same."
  (:require pallet.action-plan)
  (:require [clojure.string :as str])
  (:require fs)
  (:use [circle.util.core :only (apply-map)])
  (:use [circle.backend.build :only (log-ns build-log build-log-error checkout-dir)])
  (:require [circle.sh :as sh])
  (:require [circle.backend.ssh :as ssh])
  (:require [circle.backend.ec2 :as ec2])
  (:require [circle.backend.action :as action]))

(defn remote-bash
  "Execute bash code on the remote server.

   ssh-map is a map containing the keys :username, :public-key, :private-key :ip-addr. All keys are required."
  [ssh-map body & {:keys [environment
                          pwd]}]
  (let [cmd (sh/emit-form body
                          :environment environment
                          :pwd pwd)
        _ (build-log "running %s %s %s %s" body pwd environment cmd)
        result (ssh/remote-exec ssh-map cmd)]
    (build-log "%s returned" (-> result :exit)) ;; only log exit, rest should be handled by ssh
    result))

(defn ensure-ip-addr
  "Make sure the build's node has an ip address."
  [build]
  (if (not (-> @build :node :ip-addr))
    (do
      (assert (-> @build :instance-ids (seq)))
      (let [instance-id (-> @build :instance-ids (first))
            ip-addr (ec2/public-ip instance-id)
            new-node (merge (-> @build :node) {:ip-addr ip-addr})]
      (dosync
       (alter build assoc :node new-node))))
    build))

(defn remote-bash-build
  [build body & {:as opts}]
  (ensure-ip-addr build)
  (apply-map remote-bash (-> @build :node) body opts))

(defn action-name [cmd]
  (if (and (coll? cmd) (not (coll? (first cmd))))
    (str/join " " cmd)
    (str cmd)))

(defn bash
  "Returns a new action that executes bash on the host. Body is a
  string. If pwd is not specified, defaults to the root of the build's
  checkout dir"
  [body & {:keys [name abort-on-nonzero environment pwd]
           :or {abort-on-nonzero true}
           :as opts}]
  (let [name (or name (action-name body))]
    (action/action :name name
                   :act-fn (fn [build]
                             (let [pwd (fs/join (checkout-dir build) (or pwd "/"))
                                   result (remote-bash-build build body :environment environment :pwd pwd)]
                               (when (and (not= 0 (-> result :exit)) abort-on-nonzero)
                                 (action/abort! build (str body " returned exit code " (-> result :exit))))
                               (action/add-action-result result)
                               result)))))

