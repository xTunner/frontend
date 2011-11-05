(ns circle.backend.action
  (:require [clj-time.core :as time])
  (:use [arohner.validation :only (validate!)]
        [circle.util.predicates :only (bool? ref?)]
        [circle.util.args :only (require-args)]
        [circle.util.except :only (throw-if throw-if-not)]
        [clojure.tools.logging :only (errorf)])
  (:require [somnium.congomongo :as mongo]))

(defrecord Action [name
                   act-fn ;; an fn of one argument, the session. If returns falsy, the action has "failed" and the on-fail code is run
                   ])

(defrecord ActionResult [name ;;mandatory
                         success  ;; boolean, required
                         continue ;; boolean, required
                         start-time
                         end-time
                         out  ;; stdout from the command, a string (optional)
                         err  ;; stderr from the command, a string (optional)
                         exit ;; exit status from the command (optional)
                         ])

(def ActionResult-validator
  [[map? "result must be a map"
    :action-name "result must have a name"
    #(-> % :success bool?) ":success must be a bool, got %s" #(-> % :success (class) )]
   [#(or (-> % :success) (-> % :continue bool?)) ":continue must be a bool, got %s" #(-> % :continue (class))]])

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
  (let [obj (mongo/insert! "action_log" {})]
    (dosync
     (alter *current-action-results* assoc :_id (-> obj :_id)))))

(defn record
  [f key val]
  (dosync
   (alter *current-action-results* f key val)
   (mongo/update! "action_log"
                  (select-keys @*current-action-results* [:_id])
                  @*current-action-results*)))

(defn abort!
  "Stop the build."
  [build message]
  (errorf "Aborting build: %s" message)
  (record build assoc :continue? false :failed? true))

(defn add-start-time
  []
  (record assoc :start-time (-> (time/now) (.toDate))))

(defn add-stop-time
  []
  (record assoc :stop-time (-> (time/now) (.toDate))))

(defn add-exit-code
  [exit-code]
  (record assoc :exit-code exit-code))

(defn add-output
  "Appends stdout strings to action result"
  [data & {:keys [err-data?]}]
  (record #(merge-with str % {(if err-data? :err :out) data})))

(defn add-err
  "Appends stderr strings to action result"
  [err]
  (add-output err :err true))

(defn action-results
  "creates a new action results"
  [act]
  (ref {:name (:name act)}))

(defn add-action-result
  "Adds information about the action's result
  out - stdout, a string from running the command
  err - stdderr"
  [{:keys [out err exit-code successful continue]
      :or {successful true
           continue true}
      :as args}]
  (throw-if-not *current-action* "no action to update")
  (dosync
   (add-output out)
   (add-err err)
   (add-exit-code exit-code)))

(defmacro with-action [build act & body]
  `(do
     (let [act# ~act
           build# ~build]
       (throw-if-not (map? act#) "action must be a ref")
       (binding [*current-action* act#
                 *current-action-results* (action-results act#)]
         (dosync
          (create-mongo-obj)
          (add-start-time))
         (let [result# (do ~@body)]
           (dosync
            (add-stop-time)
            (alter build# update-in [:action-results] conj @*current-action-results*))
           result#)))))

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
