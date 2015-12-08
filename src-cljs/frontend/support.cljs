(ns frontend.support
  (:require [frontend.config :as config]
            [frontend.utils :as utils :include-macros true]
            [frontend.intercom :as intercom]
            [frontend.elevio :as elevio]
            [goog.dom :as gdom]))

(defn raise-dialog [ch]
  (try
    (if (config/elevio-enabled?)
      ((-> (aget js/window "_elev")
           (aget "openModule")) "support")
      (js/Intercom "show"))
    (catch :default e
      (utils/notify-error ch "Uh-oh, our Help system isn't available. Please email us instead, at sayhi@circleci.com")
      (utils/merror e))))

(defn enable-one!
  "If elevio is enabled, show elevio. Otherwise, disable elevio"
  []
  (if (config/elevio-enabled?)
    (elevio/enable!)
    (elevio/disable!)))
