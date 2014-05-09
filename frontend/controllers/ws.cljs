(ns frontend.controllers.ws
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [frontend.controllers.api :as api]
            [frontend.utils :as utils :refer [mlog]]
            [frontend.models.build :as build-model]
            [frontend.pusher :as pusher])
  (:require-macros [frontend.utils :refer [inspect]]))

(defmulti ws-event
  (fn [pusher-imp message args state] message))

(defmethod ws-event :default
  [pusher-imp message args state]
  (mlog "Unknown ws event: " (pr-str message))
  state)

(defmethod ws-event :build/update
  [pusher-imp message {:keys [data channel-name]} state]
  (let [build (:current-build state)]
    (if-not (= (pusher/build-channel build) channel-name)
      (do
        (mlog "Ignoring event for old build channel: " channel-name)
        state)
      (update-in state [:current-build] merge (js->clj data :keywordize-keys true)))))

(defmethod ws-event :build/new-action
  [pusher-imp message {:keys [data channel-name]} state]
  (let [build (:current-build state)]
    (if-not (= (pusher/build-channel build) channel-name)
      (do
        (mlog "Ignoring event for old build channel: " channel-name)
        state)
      (let [{step-index :step action-index :index action-log :log} (js->clj data :keywordize-keys true)]
        (-> state
            (update-in [:current-build] build-model/fill-steps step-index)
            (update-in [:current-build :steps step-index] (fnil identity {:name (:name action-log)
                                                                          :actions []}))
            (update-in [:current-build] build-model/fill-actions step-index action-index action-log)
            (assoc-in [:current-build :steps step-index :actions action-index] action-log))))))

(defmethod ws-event :build/update-action
  [pusher-imp message {:keys [data channel-name]} state]
  (let [build (:current-build state)]
    (if-not (= (pusher/build-channel build) channel-name)
      (do
        (mlog "Ignoring event for old build channel: " channel-name)
        state)
      (let [{step-index :step action-index :index action-log :log} (js->clj data :keywordize-keys true)]
        (-> state
            (update-in [:current-build] build-model/fill-steps step-index)
            (update-in [:current-build :steps step-index] (fnil identity {:name (:name action-log)
                                                                          :actions []}))
            (update-in [:current-build] build-model/fill-actions step-index action-index action-log)
            (update-in [:current-build :steps step-index :actions action-index] merge action-log))))))

(defmethod ws-event :build/append-action
  [pusher-imp message {:keys [data channel-name]} state]
  (let [build (:current-build state)]
    (if-not (= (pusher/build-channel build) channel-name)
      (do
        (mlog "Ignoring event for old build channel: " channel-name)
        state)
      (let [{step-index :step action-index :index output :out} (js->clj data :keywordize-keys true)]
        (-> state
            (update-in [:current-build] build-model/fill-steps step-index)
            (update-in [:current-build :steps step-index] (fnil identity {:actions []}))
            (update-in [:current-build] build-model/fill-actions step-index action-index {})
            (update-in [:current-build :steps step-index :actions action-index] (fnil identity {:output []}))
            (update-in [:current-build :steps step-index :actions action-index :output] conj output))))))
