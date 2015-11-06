(ns frontend.utils.launchdarkly)

(defn feature-on?
  ([feature-name default]
   (when (and js/window.ldclient js/ldclient.toggle)
    (.toggle js/ldclient feature-name default)))
  ([feature-name]
   (feature-on? feature-name nil)))

(defn identify [user]
  (when (and js/window.ldclient js/ldclient.identify)
   (.identify js/ldclient (clj->js (merge {:key (aget js/ldUser "key")} user)))))

(defn exists? [feature-name]
  (not (nil? (feature-on? feature-name nil))))
