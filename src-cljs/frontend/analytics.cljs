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

(def supported-click-and-impression-events
  ;; This is a list of our supported click and impression events.
  ;; They are in the fomat <item>-<clicked or impression>
  ;; Please add any new click and impression events here and keep the 
  ;; list alphabetically sorted, which will hep prevent duplicate events.
  ;; Events should NOT be view specific. They should be view agnostic and
  ;; include a view in the properties.
  #{:add-more-containers-clicked
    :beta-accept-terms-clicked
    :beta-join-clicked
    :beta-leave-clicked
    :branch-clicked
    :build-insights-upsell-clicked
    :build-insights-upsell-impression
    :build-tests-ad-clicked ;; need to investigate what this is
    :build-timing-upsell-clicked
    :build-timing-upsell-impression
    :cancel-plan-clicked
    :parallelism-clicked
    :pr-link-clicked
    :project-clicked
    :project-settings-clicked
    :revision-link-clicked
    :signup-clicked
    :signup-impression})

(def supported-api-response-events
  ;; This is a list of our supported api-response events.
  ;; They are in the format of <object>-<action take in the past tense>
  ;; Please add any new api-response events here and keep the list alphabetically
  ;; sorted, which will hep prevent duplicate events.
  #{:project-builds-stopped
    :container-amount-changed
    :plan-cancelled
    :project-followed
    :project-unfollowed})

(defmulti track (fn [data]
                  (when (frontend.config/analytics-enabled?)
                    (cond
                      (supported-click-and-impression-events (:event-type data)) :click-or-impression-event
                      (supported-api-response-events (:event-type data)) :api-response-event
                      :else (:event-type data)))))

(defmethod track :default [data]
  (if (frontend.config/analytics-enabled?)
    (mwarn "Cannot log an unsupported event type, please add it to the list of supported events")
    (mwarn "Analytics are currently not enabled")))

(defmethod track :click-or-impression-event [{:keys [event-type properties]}] 
  (segment/track-event (name event-type) properties))

(defmethod track :api-response-event [{:keys [event-type properties]}] 
  (segment/track-event (name event-type) properties))

(defmethod track :external-click [{:keys [event properties]}]
  (segment/track-external-click event properties))

(defmethod track :pageview [{:keys [navigation-point]}]
  (segment/track-pageview navigation-point))

(defmethod track :build-triggered [{:keys [build properties]}]
  (segment/track-event "build-triggered"  (merge {:project (vcs-url/project-name (:vcs_url build))
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

