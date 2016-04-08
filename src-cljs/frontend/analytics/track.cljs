(ns frontend.analytics.track
  (:require [frontend.state :as state]
            [frontend.analytics.core :as analytics]))

(defn image-name [flag value current-state]
  (cond
    (and (= flag :trusty-beta) value) "trusty"
    (and (= flag :trusty-beta) (not value)) "precise"
    (and (= flag :osx) value) "osx"
    ;; This means that the user has turned off osx, so we need to get
    ;; the trusty-beta flag value out of the state
    (and (= flag :osx) (not value))
    (image-name :trusty-beta
                (get-in current-state
                        (conj state/feature-flags-path :trusty-beta)))))

(defn project-image-change [{:keys [current-state flag value]}]
  (analytics/track {:event-type :change-image-clicked
                    :current-state current-state
                    :properties {:image (image-name flag value current-state)}}))
