(ns frontend.controllers.ws
  "Websocket controllers"
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [frontend.controllers.api :as api]
            [frontend.utils :as utils :refer [mlog]]
            [frontend.models.action :as action-model]
            [frontend.models.build :as build-model]
            [frontend.pusher :as pusher])
  (:require-macros [frontend.utils :refer [inspect]]))

;; To subscribe to a channel, put a subscribe message in the websocket channel
;; with the channel name and the messages you want to listen to. That will be
;; handled in the post-ws controller.
;; Example: (put! ws-ch [:subscribe {:channel-name "my-channel" :messages [:my-message]}])
;;
;; Unsubscribe by putting an unsubscribe message in the channel with the channel name
;; Exampel: (put! ws-ch [:unsubscribe "my-channel"])
;; the api-post-controller can do any other actions

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
  ;; XXX non-parallel actions need to be repeated across containers
  (let [build (:current-build state)]
    (if-not (= (pusher/build-channel build) channel-name)
      (do
        (mlog "Ignoring event for old build channel: " channel-name)
        state)
      (let [{action-index :step container-index :index action-log :log} (js->clj data :keywordize-keys true)]
        (-> state
            (update-in [:current-build :containers]
                       (fnil identity (vec (map (fn [i] {:index i})
                                                (range (:parallel build))))))
            (update-in [:current-build :containers container-index :actions]
                       (fn [actions]
                         (vec (concat actions
                                      (map (fn [i]
                                             (-> action-log
                                                 (select-keys [:index])
                                                 (assoc :step i :status "running")))
                                           (range (count actions) action-index))))))
            (assoc-in [:current-build :containers container-index :actions action-index] action-log)
            (update-in [:current-build :containers container-index :actions action-index] action-model/format-latest-output))))))

(defmethod ws-event :build/update-action
  [pusher-imp message {:keys [data channel-name]} state]
  (let [build (:current-build state)]
    (if-not (= (pusher/build-channel build) channel-name)
      (do
        (mlog "Ignoring event for old build channel: " channel-name)
        state)
      (let [{action-index :step container-index :index action-log :log} (js->clj data :keywordize-keys true)]
        (-> state
            (update-in [:current-build :containers]
                       (fnil identity (vec (map (fn [i] {:index i})
                                                (range (:parallel build))))))
            (update-in [:current-build :containers container-index :actions]
                       (fn [actions]
                         (vec (concat actions
                                      (map (fn [i]
                                             (-> action-log
                                                 (select-keys [:index])
                                                 (assoc :step i :status "running")))
                                           (range (count actions) action-index))))))
            (update-in [:current-build :containers container-index :actions action-index] merge action-log)
            ;; XXX is this necessary here?
            (update-in [:current-build :containers container-index :actions action-index] action-model/format-latest-output))))))

(defmethod ws-event :build/append-action
  [pusher-imp message {:keys [data channel-name]} state]
  (let [build (:current-build state)]
    (if-not (= (pusher/build-channel build) channel-name)
      (do
        (mlog "Ignoring event for old build channel: " channel-name)
        state)
      (let [{action-index :step container-index :index output :out} (js->clj data :keywordize-keys true)]
        (-> state
            (update-in [:current-build :containers]
                       (fnil identity (vec (map (fn [i] {:index i})
                                                (range (:parallel build))))))
            (update-in [:current-build :containers container-index :actions]
                       (fn [actions]
                         (vec (concat actions
                                      (map (fn [i]
                                             {:index container-index :step i :status "running"})
                                           (range (count actions) action-index))))))
            (update-in [:current-build :containers container-index :actions action-index :output] vec)
            (update-in [:current-build :containers container-index :actions action-index :output]
                       conj output)
            (update-in [:current-build :containers container-index :actions action-index] action-model/format-latest-output))))))
