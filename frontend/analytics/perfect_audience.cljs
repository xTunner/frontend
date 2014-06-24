(ns frontend.analytics.perfect-audience
  (:require [frontend.utils :as utils :include-macros true]))

(defn track [event]
  (utils/swallow-errors
   (let [pq (or (aget js/window "_pq") #js [])]
     ((aget pq "push") #js ["track" event]))))
