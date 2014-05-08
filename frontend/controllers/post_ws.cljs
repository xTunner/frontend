(ns frontend.controllers.post-ws
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [clojure.string :as string]
            [frontend.pusher :as pusher]
            [frontend.utils :as utils :refer [mlog merror]]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [frontend.utils :refer [inspect]]))

(defmulti post-ws-event!
  (fn [pusher-imp message args previous-state current-state] message))

(defmethod post-ws-event! :default
  [pusher-imp message args previous-state current-state]
  (mlog "No post-ws for: " message))

;; XXX: is this the best place to handle subscriptions?
(defmethod post-ws-event! :subscribe
  [pusher-imp message {:keys [channel-name messages]} previous-state current-state]
  (let [ws-ch (get-in current-state [:comms :ws])]
    (pusher/subscribe pusher-imp channel-name messages ws-ch)))

(defmethod post-ws-event! :unsubscribe
  [pusher-imp message channel-name previous-state current-state]
  (pusher/unsubscribe pusher-imp channel-name))
