(ns frontend.analytics.adroll
  (:require [frontend.utils :as utils :include-macros true]))

(defn record-payer []
  (utils/swallow-errors
   (let [adroll (aget js/window "__adroll")]
     ((aget adroll "record_user") #js {:adroll_segments "payer"}))))
