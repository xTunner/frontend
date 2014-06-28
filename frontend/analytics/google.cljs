(ns frontend.analytics.google
  (:require [frontend.utils :as utils :include-macros true]))

(defn push [args]
  (let [gaq (aget js/window "_gaq")]
     ((aget gaq "push") (clj->js args))))

(defn track-event [& args]
  (utils/swallow-errors (push "_trackEvent" args)))

(defn track-pageview [& args]
  (utils/swallow-errors (push "_trackPageview" args)))
