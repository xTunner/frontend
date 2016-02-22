(ns frontend.analytics.segment
  (:require [cljs.core.async :as async :refer  [chan close!]]
            [frontend.async :refer  [put!]]
            [frontend.utils :as utils
             :include-macros true
             :refer [clj-keys-with-dashes->js-keys-with-underscores]]
            [frontend.analytics.common :as common-analytics]
            [schema.core :as s]))

(def SegmentProperties
  ;; user and view should never be null, since they will always have values for
  ;; a logged in user.
  {:user s/Str
   :view s/Keyword
   :org  (s/maybe s/Str)
   :repo (s/maybe s/Str)
   s/Keyword s/Any})

(def LoggedOutEvent
  (merge
    SegmentProperties
    {:user (s/maybe s/Str)}))

(def UserEvent
  {:id s/Str
   :user-properties common-analytics/UserProperties})

(s/defn track-pageview [navigation-point :- s/Keyword & [properties :- SegmentProperties]]
  (utils/swallow-errors
    (js/analytics.page (name navigation-point)
                       (clj-keys-with-dashes->js-keys-with-underscores properties))))

(s/defn track-event [event :- s/Keyword & [properties :- SegmentProperties]]
  (utils/swallow-errors
    (js/analytics.track (name event)
                        (clj-keys-with-dashes->js-keys-with-underscores properties))))

(s/defn identify [event-data :- UserEvent]
  (utils/swallow-errors
    (js/analytics.identify (:id event-data) (-> event-data
                                                :user-properties
                                                clj-keys-with-dashes->js-keys-with-underscores))))

(s/defn track-external-click [event :- s/Keyword & [properties :- LoggedOutEvent]]
  (let [ch (chan)]
    (js/analytics.track (name event)
                        (clj-keys-with-dashes->js-keys-with-underscores properties)
                        #(do (put! ch %) (close! ch)))
    ch))
