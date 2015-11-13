(ns frontend.elevio
  (:require [frontend.utils :as utils]
            [goog.dom :as gdom]
            [goog.dom.classlist :as class-list]))

(defn disable! []
  (gdom/removeNode (gdom/getElement "elevio-widget"))
  (set! (.-_elevio js/window) nil))

(defn enable! []
  (class-list/add (gdom/getElement "elevio-widget") "enabled"))
