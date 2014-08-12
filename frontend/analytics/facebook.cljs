(ns frontend.analytics.facebook
  (:require [frontend.utils :as utils :include-macros true]
            [goog.net.jsloader]))


(defn track-signup 
   (if (aget js/window "_fbq")
         (track-pid pid)
         (-> (goog.net.jsloader.load "//connect.facebook.net/en_US/fbds.js")
                     (.addCallback #(track-pid pid)))))








