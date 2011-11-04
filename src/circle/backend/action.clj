(ns circle.backend.action
  (:require [clj-time.core :as time])
  (:use [arohner.validation :only (validate!)]
        [circle.util.predicates :only (bool? ref?)]
        [circle.util.args :only (require-args)]
        [circle.util.except :only (throw-if throw-if-not)]
        [clojure.tools.logging :only (errorf)]))

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

(defn abort!
  "Stop the build."
  [build message]
  (errorf "Aborting build: %s" message)
  (dosync
   (alter build assoc :continue? false)
   (alter build assoc :failed? true)))

(def ^{:dynamic true} *current-action* nil)
(def ^{:dynamic true} *current-action-results* nil)
(def ^{:dynamic true} *set-action-results* nil)

(defn add-start-time
  ([]
     (add-start-time *current-action-results*))
  ([action-result]
     (dosync
      (alter action-result assoc :start-time (time/now)))))

(defn add-stop-time
  ([]
     (add-stop-time *current-action-results*))
  ([action]
     (dosync
      (alter action assoc :stop-time (time/now)))))

(defn add-output
  "Appends stdout strings to action result"
  [action-result out & {:keys [err]}]
  (dosync
   (alter action-result #(merge-with str % {(if err
                                              :err
                                              :out) out}))))

(defn add-err
  "Appends stderr strings to action result"
  [action-result err]
  (add-output action-result err :err true))

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
   (when out
     (add-output *current-action-results* out))
   (when err
     (add-err *current-action-results* err))))

(defmacro with-action [build act & body]
  `(do
     (let [act# ~act
           build# ~build]
       (throw-if-not (map? act#) "action must be a ref")
       (binding [*current-action* act#
                 *current-action-results* (action-results act#)]
         (add-start-time *current-action-results*)
         (let [result# (do ~@body)]
           (dosync
            (add-stop-time *current-action-results*)
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
