(ns frontend.config)

(defn env
  "The name of the server configuration environment.
  For humans only: Do not gate features with this setting."
  []
  (aget js/window "renderContext" "env"))

(defn enterprise?
  "True if this is an enterprise (as opposed to main public web) deployment."
  []
  (boolean (aget js/window "renderContext" "enterprise")))

(defn pusher
  "Options to be passed to the Pusher client library."
  []
  (js->clj (aget js/window "renderContext" "pusher")))

(defn log-channels?
  "If true, log all messages on global core.async channels."
  []
  (boolean (aget js/window "renderContext" "log_channels")))
