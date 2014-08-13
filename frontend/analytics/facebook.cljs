(ns frontend.analytics.facebook
  (:require [frontend.utils :as utils :include-macros true]
            [goog.net.jsloader]))

(defn track-conversion []
  (.push js/window._fbq #js ["track" "6017231164176" {:value "0.01" :currency "USD"}]))

(defn track-signup []
  (if (aget js/window "_fbq")
    (track-conversion)
    (-> (goog.net.jsloader.load "//connect.facebook.net/en_US/fbds.js")
        (.addCallback track-conversion)))
  
  
  
  
  
  
  
  
