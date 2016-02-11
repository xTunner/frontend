(ns frontend.analytics
  (:require [frontend.analytics.adroll :as adroll]
            [frontend.analytics.perfect-audience :as pa]
            [frontend.analytics.rollbar :as rollbar]
            [frontend.analytics.segment :as segment]
            [frontend.models.build :as build-model]
            [frontend.models.project :as project-model]
            [frontend.utils :refer [mwarn]]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.intercom :as intercom]
            [frontend.utils.vcs-url :as vcs-url]
            [schema.core :as s]
            [om.core :as om :include-macros true]
            [goog.style]
            [goog.string :as gstr]))

(def AnalyticsEvent
  {:event-type s/Keyword
   :owner s/Any
   (s/optional-key :properties) {s/Keyword s/Any}})

(def PageviewEvent
  (merge
    AnalyticsEvent
    :navigation-point s/Keyword))

(def ExternalClickEvent
  (merge 
    AnalyticsEvent
    :event s/Str))

(def BuildEvent
  (merge
    AnalyticsEvent
    {:build {s/Keyword s/Any}}))

(def ViewBuildEvent
  (merge
    BuildEvent
    {:user {s/Keyword s/Any}}))

(def supported-events
  ;; This is a list of our supported events.
  ;; Events should NOT be view specific. They should be view agnostic and
  ;; include a view in the properties.
  ;; Add new events here and keep each list of event types sorted alphabetically

  #{;; These are the click and impression events.
    ;; They are in the fomat <item>-<clicked or impression>.
    :add-more-containers-clicked
    :beta-accept-terms-clicked ;; do beta events
    :beta-join-clicked ;; do beta events
    :beta-leave-clicked ;; do beta events
    :branch-clicked
    :build-insights-upsell-clicked ;; incomplete
    :build-insights-upsell-impression ;; incomplete
    :build-tests-ad-clicked ;; need to investigate what this is
    :build-timing-upsell-clicked
    :build-timing-upsell-impression
    :cancel-plan-clicked ;; maybe a new event without repo instead of the maybe on repo?
    :parallelism-clicked
    :pr-link-clicked
    :project-clicked
    :project-settings-clicked
    :revision-link-clicked
    :signup-clicked
    :signup-impression

    ;; These are the api response events.
    ;; They are in the format of <object>-<action take in the past tense>
    :project-builds-stopped
    :container-amount-changed
    :plan-cancelled
    :project-followed
    :project-unfollowed})

(defn- get-current-state-properties [owner]
  "Get a dict of the mutable states we want to track with our events
  out of the current app state."
  (let [app-state @(om/get-shared owner [:_app-state-do-not-use])
        view (get-in app-state state/current-view-path)
        user (get-in app-state state/user-login-path) 
        project (get-in app-state state/project-path)
        org (get-in app-state state/org-name-path)]
    (-> {:user user
         :view view}
        (merge (if project
                 {:repo (project-model/repo-name project)
                  :org (project-model/org-name project)}
                 {:org org
                  :repo nil})))))

(defn- add-current-state-to-props [props owner]
  "Fill in any unsupplied app state values with those in the
  current app state. Supplied data takes precedence over app data."
  (-> owner
      (get-current-state-properties)
      (merge props)))

(defmulti track (fn [data]
                  (when (frontend.config/analytics-enabled?)
                    (if (supported-events (:event-type data))
                      :track-event
                      (:event-type data)))))

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

(defmethod track :default [data]
  (if (frontend.config/analytics-enabled?)
    (mwarn "Cannot log an unsupported event type, please add it to the list of supported events")
    (mwarn "Analytics are currently not enabled")))

(defmethod track :track-event [event-data]
  (let [{:keys [event-type properties owner]} event-data]
    (segment/track-event (name event-type) (add-current-state-to-props properties owner))))

(defmethod track :new-plan-created [event-data]
  (let [{:keys [event-type properties owner]} event-data] 
    (segment/track-event "new-plan-created" properties)
    (intercom/track :paid-for-plan)))

(defmethod track :external-click [event-data]
  (let [{:keys [event properties owner]} event-data]
    (segment/track-external-click event (add-current-state-to-props properties owner))))

(defmethod track :pageview [event-data]
  (let [{:keys [navigation-point owner]} event-data]
    (segment/track-pageview (name navigation-point) (get-current-state-properties owner))))

(defmethod track :build-triggered [event-data]
  (let [{:keys [build properties owner]} event-data
        props (merge {:project (vcs-url/project-name (:vcs_url build))
                      :build-num (:build_num build)
                      :retry? true}
                     properties)]
    (segment/track-event "build-triggered" (add-current-state-to-props props owner)))) 

(defmethod track :view-build [event-data]
  (let [{:keys [build user properties owner]} event-data
        props (merge (build-properties build) properties)]
    (segment/track-event "view-build" (add-current-state-to-props props owner))
    (when (and (:oss build) (build-model/owner? build user))
      (intercom/track :viewed-self-triggered-oss-build
                      {:vcs-url (vcs-url/project-name (:vcs_url build))
                       :outcome (:outcome build)}))))

(defmethod track :init-user [{:keys [login]}]
  (utils/swallow-errors
   (rollbar/init-user login)))

