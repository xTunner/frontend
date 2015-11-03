(ns frontend.utils.launchdarkly)

(defn feature-on?
  ([feature-name default]
   (.toggle js/ldclient feature-name default))
  ([feature-name]
   (feature-on? feature-name false)))

(defn identify [user]
  (.identify js/ldclient (clj->js (merge {:key (aget js/ldUser "key")} user))))
