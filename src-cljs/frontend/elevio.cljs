(ns frontend.elevio
  (:require [frontend.utils :as utils]
            [goog.dom :as gdom]
            [goog.dom.classlist :as class-list]))

(defn disable! []
  (gdom/removeNode (gdom/getElement "elevio-widget"))
  (aset js/window "_elev" nil))

(defn enable! []
  (class-list/add (gdom/getElement "elevio-widget") "enabled"))
