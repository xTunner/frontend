(ns frontend.controllers.ws
  "Websocket controllers"
  (:require [clojure.set]
            [frontend.controllers.api :as api]
            [frontend.models.action :as action-model]
            [frontend.models.build :as build-model]
            [frontend.pusher :as pusher]
            [frontend.state :as state]
            [frontend.utils :as utils :refer [mlog]])
  (:require-macros [frontend.utils :refer [inspect]]))

;; To subscribe to a channel, put a subscribe message in the websocket channel
;; with the channel name and the messages you want to listen to. That will be
;; handled in the post-ws controller.
;; Example: (put! ws-ch [:subscribe {:channel-name "my-channel" :messages [:my-message]}])
;;
;; Unsubscribe by putting an unsubscribe message in the channel with the channel name
;; Exampel: (put! ws-ch [:unsubscribe "my-channel"])
;; the api-post-controller can do any other actions

;; --- Navigation Multimethod Declarations ---

(defmulti ws-event
  (fn [pusher-imp message args state] message))

(defmulti post-ws-event!
  (fn [pusher-imp message args previous-state current-state] message))

;; --- Navigation Mutlimethod Implementations ---

(defmethod ws-event :default
  [pusher-imp message args state]
  (mlog "Unknown ws event: " (pr-str message))
  state)

(defmethod post-ws-event! :default
  [pusher-imp message args previous-state current-state]
  (mlog "No post-ws for: " message))


(defmethod ws-event :build/update
  [pusher-imp message {:keys [data channel-name]} state]
  (let [build (get-in state state/build-path)]
    (if-not (= (pusher/build-channel build) channel-name)
      (do
        (mlog "Ignoring event for old build channel: " channel-name)
        state)
      (update-in state state/build-path merge (js->clj data :keywordize-keys true)))))


(defmethod ws-event :build/new-action
  [pusher-imp message {:keys [data channel-name]} state]
  ;; XXX non-parallel actions need to be repeated across containers
  (let [build (get-in state state/build-path)]
    (if-not (= (pusher/build-channel build) channel-name)
      (do
        (mlog "Ignoring event for old build channel: " channel-name)
        state)
      (let [{action-index :step container-index :index action-log :log} (js->clj data :keywordize-keys true)]
        (-> state
            (build-model/fill-containers container-index action-index)
            (assoc-in (state/action-path container-index action-index) action-log)
            (update-in (state/action-path container-index action-index) action-model/format-latest-output))))))


(defmethod ws-event :build/update-action
  [pusher-imp message {:keys [data channel-name]} state]
  (let [build (get-in state state/build-path)]
    (if-not (= (pusher/build-channel build) channel-name)
      (do
        (mlog "Ignoring event for old build channel: " channel-name)
        state)
      (let [{action-index :step container-index :index action-log :log} (js->clj data :keywordize-keys true)]
        (-> state
            (build-model/fill-containers container-index action-index)
            (update-in (state/action-path container-index action-index) merge action-log)
            ;; XXX is this necessary here?
            (update-in (state/action-path container-index action-index) action-model/format-latest-output))))))


(defmethod ws-event :build/append-action
  [pusher-imp message {:keys [data channel-name]} state]
  (let [build (get-in state state/build-path)
        {action-index :step container-index :index output :out} (js->clj data :keywordize-keys true)]
    (cond
     (not= (pusher/build-channel build) channel-name)
     (do (mlog "Ignoring event for old build channel: " channel-name)
         state)

     (not= container-index (get-in build state/current-container-path 0))
     (do (mlog "Ignoring output for inactive container: " container-index)
         state)

     :else
     (let [{action-index :step container-index :index output :out} (js->clj data :keywordize-keys true)]
       (-> state
           (build-model/fill-containers container-index action-index)
           (update-in (state/action-output-path container-index action-index) vec)
           (update-in (state/action-output-path container-index action-index) conj output)
           (update-in (state/action-path container-index action-index) action-model/format-latest-output))))))


(defmethod ws-event :build/add-messages
  [pusher-imp message {:keys [data channel-name]} state]
  (let [build (get-in state state/build-path)
        new-messages (set (js->clj data :keywordize-keys true))]
    (if-not (= (pusher/build-channel build) channel-name)
      (mlog "Ignoring event for old build channel: " channel-name)
      (update-in state (conj state/build-path :messages)
                 (fn [messages] (-> messages
                                    set ;; careful not to add the same message twice
                                    (clojure.set/union new-messages)))))))


;; XXX: is this the best place to handle subscriptions?
(defmethod post-ws-event! :subscribe
  [pusher-imp message {:keys [channel-name messages context]} previous-state current-state]
  (let [ws-ch (get-in current-state [:comms :ws])]
    (mlog "subscribing to " channel-name)
    (pusher/subscribe pusher-imp channel-name ws-ch :messages messages :context context)))


(defmethod post-ws-event! :unsubscribe
  [pusher-imp message channel-name previous-state current-state]
  (pusher/unsubscribe pusher-imp channel-name))
