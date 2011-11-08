(ns circle.backend.action.bash
  (:use [arohner.utils :only (inspect)])
  (:require pallet.action-plan)
  (:use [circle.util.core :only (apply-map apply-if)])
  (:use [circle.util.predicates :only (named?)])
  (:use [circle.backend.build :only (*pwd* *env* log-ns build-log build-log-error)])
  (:require [circle.backend.ssh :as ssh])
  (:require [circle.backend.ec2 :as ec2])
  (:require [circle.backend.action :as action]))

(defn env-variables [env]
  (->> env
       (map (fn [[k v]]
              (format "%s=%s" k (apply-if (named? v) name v))))
       (clojure.string/join " ")))

(defn format-bash-cmd [body]
  (str (when (seq *pwd*)
         (format "cd %s; " *pwd*))
       (when (seq *env*)
         (str (env-variables *env*) " "))
       body))

(defn remote-bash
  "Execute bash code on the remote server. Options:

  - abort-on-nonzero: Abort the build if the command returns a non-zero exit code"
  [build body & {:keys [abort-on-nonzero]
                 :or {abort-on-nonzero true}
                 :as opts}]
  (let [instance-id (-> @build :instance-ids (first))
        ip-addr (ec2/public-ip instance-id)
        ssh-map (merge (-> @build :group :circle-node-spec) {:ip-addr ip-addr})
        cmd (format-bash-cmd body)
        _ (build-log cmd)
        result (binding [ssh/handle-out build-log
                         ssh/handle-err build-log-error]
                 (ssh/remote-exec ssh-map cmd))]
    (when (and (not= 0 (-> result :exit)) abort-on-nonzero)
      (action/abort! build (str body " returned exit code " (-> result :exit))))
    (action/add-action-result result)
    result))

(defn bash
  "Returns a new action that executes bash on the host. Body is a string"
  [body & {:keys [name] :as opts}]
  (assert (string? body))
  (let [name (or name (str body))]
    (action/action :name name
                   :act-fn (fn [build]
                             (apply-map remote-bash build body opts)))))

