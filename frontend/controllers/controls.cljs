(ns frontend.controllers.controls
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [frontend.state :as state]
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
  (update-in state state/show-usage-queue-path not))

(defmethod control-event :selected-add-projects-org
  [target message args state]
  (-> state
      (assoc-in [:settings :add-projects :selected-org] args)
      (assoc-in [:settings :add-projects :repo-filter-string] "")))

(defmethod control-event :show-artifacts-toggled
  [target message build-id state]
  (update-in state state/show-artifacts-path not))

(defmethod control-event :container-selected
  [target message container-id state]
  (assoc-in state state/current-container-path container-id))

(defmethod control-event :action-log-output-toggled
  [target message {:keys [index step]} state]
  (update-in state (state/show-action-output-path index step) not))

(defmethod control-event :selected-project-parallelism
  [target message {:keys [project-id parallelism]} state]
  (assoc-in state (conj state/project-path :parallel) parallelism))

(defmethod control-event :dismiss-invite-form
  [target message _ state]
  (assoc-in state state/dismiss-invite-form-path true))

(defmethod control-event :invite-selected-all
  [target message _ state]
  (update-in state state/build-github-users-path (fn [users]
                                                   (vec (map #(assoc % :checked true) users)))))

(defmethod control-event :invite-selected-none
  [target message _ state]
  (update-in state state/build-github-users-path (fn [users]
                                                   (vec (map #(assoc % :checked false) users)))))

(defmethod control-event :dismiss-config-errors
  [target message _ state]
  (assoc-in state state/dismiss-config-errors-path true))

(defmethod control-event :edited-input
  [target message {:keys [value path]} state]
  (assoc-in state path value))

(defmethod control-event :toggled-input
  [target message {:keys [path]} state]
  (update-in state path not))
