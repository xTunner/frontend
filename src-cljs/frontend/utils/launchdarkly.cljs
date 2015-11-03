(ns frontend.utils.launchdarkly)

(defn feature-on?
  ([feature-name default]
   (.toggle js/ldclient feature-name default))
  ([feature-name]
   (feature-on? feature-name false)))
