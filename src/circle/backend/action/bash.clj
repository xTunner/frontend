(ns circle.backend.action.bash
  (:use [arohner.utils :only (inspect)])
  (:require pallet.action-plan)
  (:use [circle.util.core :only (apply-map)])
  (:use [circle.backend.build :only (*pwd* log-ns build-log build-log-error)])
  (:require [circle.backend.ssh :as ssh])
  (:require [circle.backend.ec2 :as ec2])
  (:require [circle.backend.action :as action]))

(defmacro remote-bash
  "Execute bash code on the remote server. Options:

  - abort-on-nonzero: Abort the build if the command returns a non-zero exit code"
  [build body & {:keys [abort-on-nonzero]
                 :or {abort-on-nonzero true}
                 :as opts}]
  (let [body (if (string? body)
               [body]
               body)]
    ;; TODO yes, this whole thing shouldn't be in macro context, but
    ;; the stevedor/script is the important part, and we can't apply
    ;; macro (yet). See c.c.macros
    `(let [build# ~build
           instance-id# (-> build# (deref) :instance-ids (first))
           ip-addr# (ec2/public-ip instance-id#)
           ssh-map# (merge (-> build# (deref) :group :circle-node-spec) {:ip-addr ip-addr#})
           cmd# (pallet.script/with-script-context (pallet.action-plan/script-template-for-server (-> build# (deref) :nodes (first)))
                  (pallet.stevedore/with-script-language :pallet.stevedore.bash/bash
                    (pallet.stevedore/script ("cd" (clj *pwd*))
                                             ~@body)))
           _# (build-log cmd#)
           result# (binding [ssh/handle-out build-log
                             ssh/handle-err build-log-error]
                     (ssh/remote-exec ssh-map# cmd#))]
       (when (and (not= 0 (-> result# :exit)) ~abort-on-nonzero)
         (action/abort! build# (str (quote ~body) " returned exit code " (-> result# :exit))))
       (action/add-action-result result#)
       result#)))

(defmacro bash
  "Returns a new action that executes bash on the host. Body is either a string, or a seq of stevedore code."
  [body & {:keys [name] :as opts}]
  (let [name (or name (str body))
        body (if (string? body)
               [body]
               body)]
    `(action/action :name ~name
                    :act-fn (fn [build#]
                              (remote-bash build# ~body ~@(flatten (seq opts)))))))

