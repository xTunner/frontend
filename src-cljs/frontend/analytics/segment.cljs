(ns frontend.analytics.segment
  (:require [frontend.utils :as utils :include-macros true]))

(defn track-pageview [navigation-point]
  (js/analytics.page (name navigation-point)))
