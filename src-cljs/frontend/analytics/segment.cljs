(ns frontend.analytics.segment
  (:require [cljs.core.async :as async :refer  [chan close!]]
            [frontend.async :refer  [put!]]
            [frontend.utils :as utils :include-macros true]
            [frontend.datetime :refer  [unix-timestamp]]))

(defn track-pageview [navigation-point]
  (utils/swallow-errors
    (js/analytics.page (name navigation-point))))

(defn track-event [event & [properties]]
  (println event)
  (utils/swallow-errors
    (js/analytics.track event (clj->js properties))))

(defn managed-track [event & [props]]
  (let [ch (chan)]
    (js/analytics.track event props
                        #(do (put! ch %) (close! ch)))
    ch))
