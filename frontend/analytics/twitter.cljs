(ns frontend.analytics.twitter
  (:require [frontend.utils :as utils :include-macros true]
            [goog.net.jsloader]))

(defn track-pid [pid]
  (let [twttr (aget js/window "twttr")
        conversion (aget twttr "conversion")
        track (aget conversion "trackPid")]
    (track pid)))

(defn track-conversion
  "Twitter has a separate pid for each type of conversion. Loads
   twitter if necessary and sends the conversion."
  [pid]
  (if (aget js/window "twttr")
    (track-pid pid)
    (-> (goog.net.jsloader.load "https://checkout.stripe.com/v2/checkout.js")
        (.addCallback #(track-pid pid)))))

(defn track-signup []
  (utils/swallow-errors
   (track-conversion "l4lg6")))

(defn track-payer []
  (utils/swallow-errors
   (track-conversion "l4m9v")))
