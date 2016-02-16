(ns frontend.analytics.segment
  (:require [cljs.core.async :as async :refer  [chan close!]]
            [frontend.async :refer  [put!]]
            [frontend.utils :as utils :include-macros true]
            [frontend.datetime :refer  [unix-timestamp]]))

(defn track-pageview [navigation-point & [properties]]
  (utils/swallow-errors
    (js/analytics.page (name navigation-point) (clj->js properties))))

(defn track-event [event & [properties]]
  (utils/swallow-errors
    (js/analytics.track event (clj->js properties))))

(defn track-external-click [event & [properties]]
  (let [ch (chan)]
    (js/analytics.track event properties
                        #(do (put! ch %) (close! ch)))
    ch))
