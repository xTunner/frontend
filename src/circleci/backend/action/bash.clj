(ns circleci.backend.action.bash
  (:use [arohner.utils :only (inspect) ])
  (:require [circleci.backend.action :as action]))

(defn exit-status
  "takes the output map from a call to ssh-exec, returns truthy if the exit status is 0"
  [out]
  (zero? (-> out :exit)))

(def ^{:dynamic true
       :doc "Binding for present working directory commands will run in"} *pwd* "")

(defmacro bash
  "Returns a new action that executes bash on the host. Body is either a string, or a seq of stevedore code."
  [name body & {:keys [] :as opts}]
  (let [body (if (string? body)
               [body]
               body)]
    `(action/action
      name
      :act-fn (fn [node#]
                (let [~'pwd *pwd*]
                  (circleci.backend.nodes/ssh-exec
                   node#
                   (pallet.script/with-script-context (pallet.action-plan/script-template-for-server node#)
                     (pallet.stevedore/with-script-language :pallet.stevedore.bash/bash
                       (pallet.stevedore/script ("cd" (clj *pwd*))
                                                ~@body)))))))))

(defmacro with-pwd [dir & body]
  `(binding [*pwd* ~dir]
     ~@body))

