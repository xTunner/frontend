(ns frontend.analytics.google
  (:require [frontend.utils :as utils :include-macros true]))

(defn push [args]
  (let [gaq (aget js/window "_gaq")
        push (aget gaq "push")]
     (.call push gaq (clj->js args))))

(defn track-event [& args]
  (utils/swallow-errors (push (cons "_trackEvent" args))))

(defn track-pageview [& args]
  (utils/swallow-errors (push (cons "_trackPageview" args))))
