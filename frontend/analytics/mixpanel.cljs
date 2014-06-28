(ns frontend.analytics.mixpanel
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [frontend.utils :as utils :include-macros true]))

(defn track [event & [props]]
  (utils/swallow-errors (js/mixpanel.track event (clj->js props))))

(defn track-pageview []
  (utils/swallow-errors (js/mixpanel.track_pageview )))

(defn register-once [props]
  (utils/swallow-errors (js/mixpanel.register_once (clj->js props))))

(defn name-tag [username]
  (utils/swallow-errors (js/mixpanel.name_tag username)))

(defn identify [username]
  (utils/swallow-errors (js/mixpanel.identify username)))

(defn managed-track [event & [props]]
  (let [ch (chan)]
    (js/mixpanel.track event (clj->js props)
                       #(do (put! ch %) (close! ch)))
    ch))
