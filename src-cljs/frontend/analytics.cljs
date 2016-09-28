(ns frontend.analytics
  (:require [frontend.analytics.core :as analytics]
            [om.next :as om-next]))

(defprotocol Properties
  (properties [this]
    "Returns a map of analytics properties to be used with events tracked within
    this component."))

(defn event
  "Builds an analytics event for a component. c is the current component. event
  is an event. The event will be given the properties specified by the component
  and by components up its parent chain, with children's properties overriding
  parents'."
  [c event]
  (if-not c
    event
    (let [component-properties (if (implements? Properties c)
                                 (properties c)
                                 {})]
      (recur (om-next/parent c)
             (update event :properties (partial merge component-properties))))))

(defn track!
  "Track an analytics event for a component."
  [c e]
  (analytics/track (event c e)))
