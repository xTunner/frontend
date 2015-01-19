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
  (assoc (js->clj (aget js/window "renderContext" "pusher"))
         :key (aget js/window "renderContext" "pusherAppKey")))

(defn logging-enabled?
  []
  "If true, log statements print to the browswer's JavaScript console."
  (boolean (aget js/window "renderContext" "logging_enabled")))

(defn log-channels?
  "If true, log all messages on global core.async channels."
  []
  (boolean (aget js/window "renderContext" "log_channels")))

(defn assets-root
  "Path to root of CDN assets."
  []
  (aget js/window "renderContext" "assetsRoot"))

(defn github-endpoint
  "Full HTTP URL of GitHub API."
  []
  (aget js/window "renderContext" "githubHttpEndpoint"))

(defn stripe-key
  "Publishable key to identify our account with Stripe.
  See: https://stripe.com/docs/tutorials/dashboard#api-keys"
  []
  (aget js/window "renderContext" "stripePublishableKey"))
