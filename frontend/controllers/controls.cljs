(ns frontend.controllers.controls
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [frontend.utils :as utils :refer [mlog]]))

(defmulti control-event
  ;; target is the DOM node at the top level for the app
  ;; message is the dispatch method (1st arg in the channel vector)
  ;; state is current state of the app
  ;; return value is the new state
  (fn [target message args state] message))

(defmethod control-event :default
  [target message args state]
  (mlog "Unknown controls: " message)
  state)

(defmethod control-event :user-menu-toggled
  [target message _ state]
  (update-in state [:settings :menus :user :open] not))

(defmethod control-event :show-all-branches-toggled
  [target message project-id state]
  (update-in state [:settings :projects project-id :show-all-branches] not))

(defmethod control-event :build-inspected
  [target message [project-id build-num] state]
  (assoc-in state [:inspected-project] {:project project-id
                                        :build-num build-num}))

(defmethod control-event :state-restored
  [target message path state]
  (let [str-data (.getItem js/localStorage "circle-state")]
    (if (seq str-data)
      (-> str-data
          reader/read-string
          (assoc :comms (:comms state)))
      state)))

(defmethod control-event :usage-queue-why-toggled
  [target message {:keys [build-id]} state]
  (update-in state [:current-build :show-usage-queue] not))

(defmethod control-event :selected-add-projects-org
  [target message args state]
  (-> state
      (assoc-in [:settings :add-projects :selected-org] args)
      (assoc-in [:settings :add-projects :repo-filter-string] "")))

(defmethod control-event :show-artifacts-toggled
  [target message build-id state]
  (update-in state [:current-build :show-artifacts] not))

(defmethod control-event :container-selected
  [target message container-id state]
  (assoc-in state [:current-build :current-container-id] container-id))

(defmethod control-event :action-log-output-toggled
  [target message {:keys [index step]} state]
  (update-in state [:current-build :steps step :actions index :show-output] not))

(defmethod control-event :selected-project-parallelism
  [target message {:keys [project-id parallelism]} state]
  (assoc-in state [:current-project :parallel] parallelism))

(defmethod control-event :edited-input
  [target message {:keys [value path]} state]
  (assoc-in state path value))
