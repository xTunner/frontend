(ns frontend.pusher
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            ;; XXX remove browser repl in prod
            [clojure.browser.repl :as repl]
            [dommy.core :as dommy]
            [goog.dom.DomHelper]
            [goog.events]
            [om.core :as om :include-macros true]
            [frontend.env :as env]
            [frontend.utils :as utils :include-macros true]
            [secretary.core :as sec])

  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(def pusher-key (if (env/production?)
                  "961a1a02aa390c0a446d"
                  "3f8cb51e8a23a178f974"))

(defn new-pusher-instance [& {:keys [key]
                              :or {key pusher-key}}]
  (aset (aget js/window "Pusher") "channel_auth_endpoint" "/auth/pusher")
  (js/window.Pusher. key (clj->js {:encrypted true
                                   :auth {:params {:CSRFToken (utils/inspect (utils/csrf-token))}}
                                   ;; this doesn't seem to work (outdated client library?)
                                   :authEndpoint "/auth/pusher"})))

(defn user-channel [user]
  (str "private-" (:login user)))

;; TODO: use the same event names on the backend as we do on the frontend
(def event-translations
  {:build/new-action "newAction"
   :build/update-action "updateAction"
   :build/append-action "appendAction"
   :build/update "updateObservables"
   :build/add-messages "maybeAddMessages"
   ;; this is kind of special, it can call any function on the old VM
   ;; luckily, it only calls refreshBuildState
   :refresh "call"})

(defn subscribe
  "Subscribes to channel and binds to events. Takes a pusher-instance,
  a channel-name, a list of messages to subscribe to and a websocket channel.
  Will put data from the pusher events onto the websocket
  channel with the message. Returns the channel."
  [pusher-instance channel-name messages ws-ch]
  (let [channel (utils/inspect (.subscribe pusher-instance (utils/inspect channel-name)))]
    (doseq [message messages
            :let [pusher-event (get event-translations message)]]
      (.bind channel pusher-event #(put! ws-ch [message %])))
    (.bind channel "pusher:subscription_error"
           #(put! ws-ch [:subscription-error {:channel-name channel-name
                                              :status %}]))
    channel))

(defn unsubscribe [pusher-instance channel-name]
  (.unsubscribe pusher-instance channel-name))
