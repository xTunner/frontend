(ns frontend.analytics.mixpanel
  (:require [frontend.utils :as utils :include-macros true]))

(defn track [event & [props]]
  (utils/swallow-errors (js/mixpanel.track event (clj->js props))))
