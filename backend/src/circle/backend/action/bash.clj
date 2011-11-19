(ns circle.backend.action.bash
  "functions for running bash on remote instances, and build actions for same."
  (:use [arohner.utils :only (inspect)])
  (:require pallet.action-plan)
  (:use [circle.util.core :only (apply-map)])
  (:use [circle.backend.build :only (*env* log-ns build-log build-log-error)])
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
        _ (build-log "running %s" body pwd environment cmd)
        result (ssh/remote-exec ssh-map cmd)]
    result))

(defn ssh-map-for-build [build]
  (let [instance-id (-> @build :instance-ids (first))
        ip-addr (ec2/public-ip instance-id)]
    (merge (-> @build :group :circle-node-spec) {:ip-addr ip-addr})))

(defn remote-bash-build
  [build body & {:as opts}]
  (apply-map remote-bash (ssh-map-for-build build) body opts))

(defn bash
  "Returns a new action that executes bash on the host. Body is a string"
  [body & {:keys [name abort-on-nonzero environment pwd]
           :or {abort-on-nonzero true}
           :as opts}]
  (let [name (or name (str body))]
    (action/action :name name
                   :act-fn (fn [build]
                             (binding [ssh/handle-out build-log
                                       ssh/handle-err build-log-error]
                               (let [result (remote-bash-build build body :environment environment :pwd pwd)]
                                 (when (and (not= 0 (-> result :exit)) abort-on-nonzero)
                                   (action/abort! build (str body " returned exit code " (-> result :exit))))
                                 (action/add-action-result result)
                                 result))))))

