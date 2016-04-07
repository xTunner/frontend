(ns frontend.analytics.track
  (:require [frontend.analytics]))

(def trusty-beta trusty-beta)

(defn- linux-name-for-flag-and-value [flag value]
  (when (not (nil? value))
    (cond
    (and (= flag trusty-beta) value) "trusty"
    (and (= flag trusty-beta) (not value)) "precise")))

(defn project-image-change [{:keys [current-state flag value]}]
  (let [image (cond
                (= flag trusty-beta) (linux-name-for-flag-and-value flag value)
                (and (= flag :osx) value) "osx"
                ;; This means that the user has turned off osx, so we need to get
                ;; the linux image out of the state
                (and (= flag :osx) (not value)) (some->> (conj state/project-path :feature_flags trusty-beta) 
                                                         (get-in state)
                                                         (linux-name-for-flag-and-value trusty-beta)))]
    (analytics/track {:event-type :change-image-clicked
                      :current-state current-state
                      :properties {:image image}})))
