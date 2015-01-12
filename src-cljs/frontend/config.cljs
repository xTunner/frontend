(ns frontend.config)

(defn enterprise? []
  (boolean (aget js/window "renderContext" "enterprise")))
