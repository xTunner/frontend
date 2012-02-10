(ns circle.backend.action
  (:require [circle.util.time :as time])
  (:use [circle.util.predicates :only (bool? ref?)]
        [circle.util.core :only (conj-vec)]
        [circle.util.args :only (require-args)]
        [circle.util.except :only (throw-if throw-if-not)]
        [circle.util.model-validation :only (validate!)]
        [circle.util.model-validation-helpers :only (is-map? require-keys col-predicate maybe)]
        [clojure.tools.logging :only (errorf)])
  (:require [circle.backend.ssh :as ssh])
  (:require [robert.hooke :as hooke])
  (:require [somnium.congomongo :as mongo]))

(def action-log-coll :action_logs)

(defrecord Action [name
                   act-fn ;; an fn of one argument, the session. If returns falsy, the action has "failed" and the on-fail code is run
                   ])

(def ActionResult-validator
  [(is-map?)
   (require-keys [:name :start_time :end_time])
   (col-predicate :success (maybe bool?) ":success must be a bool")
   (col-predicate :continue (maybe bool?) ":continue must be a bool")
   (col-predicate :out (maybe vector?) ":out must be a vector")
   (col-predicate :out #(every? map? %) "items in :out must be maps")])

(defn validate-action-result! [ar]
  (validate! ActionResult-validator ar))

(defn action
  "defines an action."
  [& {:keys [name act-fn]
      :as args}]
  (require-args name act-fn)
  args)

(defn successful? [action-result]
  (-> action-result :success))

(defn continue? [action-result]
  (or (-> action-result :success)
      (-> action-result :continue)))


(def ^{:dynamic true} *current-action* nil)
(def ^{:dynamic true} *current-action-results* nil)

(defn create-mongo-obj
  "Start recording an action in the DB. Save the Mongo ID in the action for later records"
  []
  (let [obj (mongo/insert! action-log-coll {})]
    (dosync
     (alter *current-action-results* assoc :_id (-> obj :_id)))))

(defn record
  [f & args]
  (dosync
   (apply alter *current-action-results* f args))
  (mongo/update! action-log-coll
                 (select-keys @*current-action-results* [:_id])
                 @*current-action-results*))

(defn add-output
  "Appends stdout strings to action result"
  [data & {:keys [column]
           :or {column :out}}]
  (record (fn [action-result]
            (update-in action-result [:out] conj-vec {:type column
                                                      :time (time/ju-now)
                                                      :message data}))))

(defn abort!
  "Stop the build."
  [build message ]
  (errorf "Aborting build: %s" message)
  (add-output message :column :err)
  (dosync
   (alter build assoc :continue? false :failed true)))

(defn abort-timeout!
  "Abort the build due to a command timeout"
  [build message]
  (dosync
   (abort! build message)
   (alter build assoc :timedout true)
   (record assoc :timedout true)))

(defn add-start-time
  []
  (record assoc :start_time (time/ju-now)))

(defn add-end-time
  []
  (record assoc :end_time (time/ju-now)))

(defn add-exit-code
  [exit-code]
  (record assoc :exit_code exit-code))

(defn add-err
  "Appends stderr strings to action result"
  [err]
  (add-output err :column :err))

(defn action-results
  "creates a new action results"
  [build act]
  (-> act
      (dissoc :act-fn)
      (assoc :_build-ref (-> @build :_id))
      ref))

(defn add-action-result
  "Adds information about the action's result
  out - stdout, a string from running the command
  err - stdderr"
  [{:keys [out err exit successful continue]
      :or {successful true
           continue true}
    :as args}]
  (throw-if-not *current-action* "no action to update")
  (dosync
   (when out
     (add-output out))
   (when err
     (add-err err))
   (when exit
     (add-exit-code exit))))

(defn update-out-hook [f str]
  (when *current-action-results*
    (add-output str))
  (f str))

(defn update-err-hook [f str]
  (when *current-action-results*
    (add-err str))
  (f str))

(hooke/add-hook #'ssh/handle-out update-out-hook)
(hooke/add-hook #'ssh/handle-error update-err-hook)

;; TODO: migration :type x => :source x
(defn set-source [action source]
  (assoc action :source source))

(defn set-type [action type]
  (assoc action :type type))

(defn run-action [build act]
  (throw-if-not (map? act) "action must be a map")
  (binding [*current-action* act
            *current-action-results* (action-results build act)]
    (dosync
     (create-mongo-obj)
     (add-start-time))
    (try
      ((-> act :act-fn) build)
      (catch Exception e
        (record assoc :infrastructure_fail true)
        (throw e))
      (finally
       (dosync
        (add-end-time)
        (validate-action-result! @*current-action-results*)
        (alter build update-in [:action-results] conj @*current-action-results*))))))

(defmacro defaction [name defn-args action-map f]
  (throw-if-not (vector? defn-args) "defn args must be a vector")
  (throw-if-not (map? action-map) "action-map must be a map")
  `(defn ~name ~defn-args
     (let [act-map# ~action-map
           act-name# (or (:name act-map#) (quote ~name))
           f# ~f]
       (throw-if-not (fn? f#) "f must be a fn")
       (action
        :name act-name#
        :act-fn f#))))
