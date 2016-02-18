(ns frontend.analytics.segment
  (:require [cljs.core.async :as async :refer  [chan close!]]
            [frontend.async :refer  [put!]]
            [frontend.utils :as utils :include-macros true]
            [schema.core :as s]))

(def KeywordOrString (s/conditional keyword? s/Keyword :else s/Str))

(def SegmentProperties
  ;; user and view should never be null, since they will always have values for
  ;; a logged in user.
  {:user s/Str
   :view KeywordOrString
   :org  (s/maybe s/Str)
   :repo (s/maybe s/Str)
   s/Keyword s/Any})

(def LoggedOutEvent
  (merge
    SegmentProperties
    {:user (s/maybe s/Str)}))

(s/defn track-pageview [navigation-point :- KeywordOrString & [properties :- SegmentProperties]]
  (utils/swallow-errors
    (js/analytics.page (name navigation-point) (clj->js properties))))

(s/defn track-event [event :- KeywordOrString & [properties :- SegmentProperties]]
  (utils/swallow-errors
    (js/analytics.track (name event) (clj->js properties))))

(s/defn track-external-click [event :- KeywordOrString & [properties :- LoggedOutEvent]]
  (let [ch (chan)]
    (js/analytics.track (name event) (clj->js properties)
                        #(do (put! ch %) (close! ch)))
    ch))
