(ns frontend.analytics
  (:require [frontend.analytics.adroll :as adroll]
            [frontend.analytics.perfect-audience :as pa]
            [frontend.analytics.rollbar :as rollbar]
            [frontend.analytics.segment :as segment]
            [frontend.models.build :as build-model]
            [frontend.utils :refer [mwarn]]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.intercom :as intercom]
            [frontend.utils.vcs-url :as vcs-url]
            [schema.core :as s]
            [goog.style]
            [goog.string :as gstr]))

(def supported-events
  ;; This is a list of supported event types. This is to prevent the creation
  ;; of multiple events of the same type with slightly different names throughout
  ;; the code base.
  ;; Please keep this alphabetically sorted for ease of readability.
  #{:add-containers-more-clicked
    :beta-join-click
    :beta-leave-click
    :beta-terms-accept
    :branch-list-branch-clicked
    :branch-list-project-settings-clicked
    :build-card-pr-link-clicked
    :build-card-revision-link-clicked
    :build-insights-upsell-click
    :build-insights-upsell-impression
    :build-page-pr-link-clicked
    :build-page-revision-link-clicked
    :build-tests-ad-click
    :build-timing-upsell-click
    :build-timing-upsell-impression
    :cancel-plan-clicked
    :change-container-amount
    :follow-project
    :parallelism-build-header-click
    :signup-click
    :signup-impression
    :stop-building-project
    :unfollow-project})

(defmulti track (fn [data]
                  (println "inside track")
                  (when (frontend.config/analytics-enabled?)
                    (if (supported-events (:event-type data))
                      :event
                      (:event-type data)))))

(defmethod track :default [data]
  (mwarn "Cannot log an unsupported event type, please add it to the list of supportd events"))

(defmethod track :event [{:keys [event-type properties]}] 
  (segment/track-event (name event-type) properties))

(defmethod track :external [{:keys [event properties]}]
  (segment/track-external-click event properties))

(defmethod track :pageview [{:keys [navigation-point]}]
  (segment/track-pageview navigation-point))

(defmethod track :trigger-build [{:keys [build properties]}]
  (segment/track-event "trigger-build"  (merge {:project (vcs-url/project-name (:vcs_url build))
                                                :build-num (:build_num build)
                                                :retry? true}
                                               properties)))

(defmethod track :new-plan-created [{:keys [properties]}]
  (segment/track-event "new-plan-created" properties)
  (intercom/track :paid-for-plan))

(defn build-properties [build]
  (merge {:running (build-model/running? build)
          :build-num (:build_num build)
          :repo (vcs-url/repo-name (:vcs_url build))
          :org (vcs-url/org-name (:vcs_url build))
          :oss (boolean (:oss build))
          :outcome (:outcome build)}
         (when (:stop_time build)
           {:elapsed_hours (/ (- (.getTime (js/Date.))
                                 (.getTime (js/Date. (:stop_time build))))
                              1000 60 60)})))

(defmethod track :build [{:keys [user build]}]
  (segment/track-event "view-build" (build-properties build))
  (when (and (:oss build) (build-model/owner? build user))
    (intercom/track :viewed-self-triggered-oss-build
                    {:vcs-url (vcs-url/project-name (:vcs_url build))
                     :outcome (:outcome build)})))

(defmethod track :init-user [{:keys [login]}]
  (utils/swallow-errors
   (rollbar/init-user login)))

