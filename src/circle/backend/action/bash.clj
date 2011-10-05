(ns circle.backend.action.bash
  (:use [arohner.utils :only (inspect)])
  (:require [circle.backend.ssh :as ssh])
  (:require [circle.backend.action :as action]))

(defn exit-status
  "takes the output map from a call to ssh-exec, returns truthy if the exit status is 0"
  [out]
  (zero? (-> out :exit)))

(def ^{:dynamic true
       :doc "Binding for present working directory commands will run in"} *pwd* "")

(defn process-result
  "Given the output map from ssh-exec, decide whether the action succeeded."
  [out]
  (if (= 0 (-> out :exit))
    (merge out {:success true
                :continue true})
    (merge out {:success false
                :continue false})))

(defmacro remote-bash
  [context body & {:as opts}]
  (let [body (if (string? body)
               [body]
               body)]
    ;; TODO yes, this whole thing shouldn't be in macro context, but
    ;; the stevedor/script is the important part, and we can't apply
    ;; macro (yet). See c.c.macross
    `(let [result# (ssh/ssh-exec
                    (-> ~context :node)
                    (pallet.script/with-script-context (pallet.action-plan/script-template-for-server (-> ~context :node))
                      (pallet.stevedore/with-script-language :pallet.stevedore.bash/bash
                        (pallet.stevedore/script ("cd" (clj *pwd*))
                                                 ~@body))))]
       (process-result result#))))

(defmacro bash
  "Returns a new action that executes bash on the host. Body is either a string, or a seq of stevedore code."
  [body & {:keys [name] :as opts}]
  (let [body (if (string? body)
               [body]
               body)]
    `(action/action
      :name (or ~name (str (quote ~@body)))
      :act-fn (fn [context#]
                (remote-bash context# ~body ~@(flatten (seq opts)))))))

(defmacro with-pwd
  "When set, each bash command will start in the specified directory. Dir is a string."
  [dir & body]
  `(binding [*pwd* ~dir]
     ~@body))

