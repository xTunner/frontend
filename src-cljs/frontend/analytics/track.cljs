(ns frontend.analytics.track
  (:require [frontend.state :as state]
            [om.core :as om :include-macros true]
            [frontend.analytics.core :as analytics]))

(def trusty-beta-flag :trusty-beta)

(def osx-flag :osx)

(defn image-name [flag value current-state]
  (cond
    (and (= flag trusty-beta-flag) value) "trusty"
    (and (= flag trusty-beta-flag) (not value)) "precise"
    (and (= flag osx-flag) value) "osx"
    ;; This means that the user has turned off osx, so we need to get
    ;; the trusty-beta flag value out of the state
    (and (= flag osx-flag) (not value))
    (image-name trusty-beta-flag
                (get-in current-state
                        (conj state/feature-flags-path trusty-beta-flag)))))

(defn project-image-change [{:keys [previous-state current-state flag value]}]
  (analytics/track {:event-type :change-image-clicked
                    :current-state current-state
                    :properties {:new-image (image-name flag value current-state)
                                 :flag-changed flag
                                 :value-changed-to value
                                 :new-osx-feature-flag (get-in current-state (conj state/feature-flags-path osx-flag))
                                 :new-trusty-beta-feature-flag (get-in current-state (conj state/feature-flags-path trusty-beta-flag))
                                 :previous-osx-feature-flag (get-in previous-state (conj state/feature-flags-path osx-flag))
                                 :previous-trusty-beta-feature-flag (get-in previous-state (conj state/feature-flags-path trusty-beta-flag))}}))

(defn update-plan-clicked [{:keys [new-plan previous-plan plan-type upgrade? owner]}]
  ((om/get-shared owner :track-event) {:event-type :update-plan-clicked
                                       :properties {:plan-type plan-type
                                                    :new-plan new-plan
                                                    :previous-plan previous-plan
                                                    :is-upgrade upgrade?}}))
