(ns frontend.config)

(defn enterprise? []
  (boolean (aget js/window "renderContext" "enterprise")))

(defn pusher []
  (js->clj (aget js/window "renderContext" "pusher")))
