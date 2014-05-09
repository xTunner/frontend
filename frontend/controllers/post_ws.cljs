(ns frontend.controllers.post-ws
  "Websocket post-controllers, handles subscribing to channels"
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [clojure.string :as string]
            [frontend.pusher :as pusher]
            [frontend.utils :as utils :refer [mlog merror]]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [frontend.utils :refer [inspect]]))

;; To subscribe to a channel, put a subscribe message in the websocket channel
;; with the channel name and the messages you want to listen to. That will be
;; handled in the post-ws controller.
;; Example: (put! ws-ch [:subscribe {:channel-name "my-channel" :messages [:my-message]}])
;;
;; Unsubscribe by putting an unsubscribe message in the channel with the channel name
;; Exampel: (put! ws-ch [:unsubscribe "my-channel"])
;; the api-post-controller can do any other actions

(defmulti post-ws-event!
  (fn [pusher-imp message args previous-state current-state] message))

(defmethod post-ws-event! :default
  [pusher-imp message args previous-state current-state]
  (mlog "No post-ws for: " message))

;; XXX: is this the best place to handle subscriptions?
(defmethod post-ws-event! :subscribe
  [pusher-imp message {:keys [channel-name messages context]} previous-state current-state]
  (let [ws-ch (get-in current-state [:comms :ws])]
    (mlog "subscribing to " channel-name)
    (pusher/subscribe pusher-imp channel-name ws-ch :messages messages :context context)))

(defmethod post-ws-event! :unsubscribe
  [pusher-imp message channel-name previous-state current-state]
  (pusher/unsubscribe pusher-imp channel-name))
