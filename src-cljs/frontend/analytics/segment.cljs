(ns frontend.analytics.segment
  (:require [cljs.core.async :as async :refer  [chan close!]]
            [frontend.async :refer  [put!]]
            [frontend.utils :as utils :include-macros true]
            [schema.core :as s]))

(def SegmentProperties
  ;; user and view should never be null, since they will always have values for
  ;; a logged in user.
  {:user s/Str
   :view s/Str
   (s/optinal-key :org) s/Str
   (s/optinal-key :repo) s/Str
   s/Keyword s/Str})

(s/defn track-pageview [navigation-point :- s/Str & [properties :- SegmentProperties]]
  (utils/swallow-errors
    (js/analytics.page (name navigation-point) (clj->js properties))))

(s/defn track-event [event :- s/Str & [properties :- SegmentProperties]]
  (utils/swallow-errors
    (js/analytics.track event (clj->js properties))))

(s/defn track-external-click [event :- s/Str & [properties :- SegmentProperties]]
  (let [ch (chan)]
    (js/analytics.track event properties
                        #(do (put! ch %) (close! ch)))
    ch))
