(ns frontend.analytics.mixpanel
  (:require [frontend.utils :as utils :include-macros true]))

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
