(ns frontend.analytics
  (:require [frontend.analytics.adroll :as adroll]
            [frontend.analytics.perfect-audience :as pa]
            [frontend.analytics.rollbar :as rollbar]
            [frontend.analytics.segment :as segment]
            [frontend.models.build :as build-model]
            [frontend.models.project :as project-model]
            [frontend.utils :refer [log-in-dev]]
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

(def AnalyticsEventForControllers
  {:event-type s/Keyword
   :current-state s/Any
   (s/optional-key :properties) {s/Keyword s/Any}} )

(def PageviewEvent
  (merge
    AnalyticsEventForControllers
    {:navigation-point s/Keyword}))

(def ExternalClickEvent
  (merge 
    AnalyticsEvent
    {:event s/Str}))

(def BuildEvent
  (merge
    AnalyticsEvent
    {:build {s/Keyword s/Any}}))

(def ViewBuildEvent
  (merge
    BuildEvent
    {:user {s/Keyword s/Any}}))

;; Below are the lists of our supported events.
;; Events should NOT be view specific. They should be view agnostic and
;; include a view in the properties.
;; Add new events here and keep each list of event types sorted alphabetically
(def supported-click-and-impression-events
  ;; These are the click and impression events.
  ;; They are in the fomat <item>-<clicked or impression>.
  #{:add-more-containers-clicked
    :beta-accept-terms-clicked
    :beta-join-clicked
    :beta-leave-clicked
    :branch-clicked
    :build-insights-upsell-clicked
    :build-insights-upsell-impression
    :build-timing-upsell-clicked
    :build-timing-upsell-impression
    :cancel-plan-clicked
    :parallelism-clicked
    :pr-link-clicked
    :project-clicked
    :project-settings-clicked
    :revision-link-clicked
    :set-up-junit-clicked
    :signup-clicked
    :signup-impression})

(def supported-api-response-events
  ;; These are the api response events.
  ;; They are in the format of <object>-<action take in the past tense>
  #{:container-amount-changed
    :plan-cancelled
    :project-builds-stopped
    :project-followed
    :project-unfollowed})

(defn- get-properties-from-state [state]
  "Get a dict of the mutable properties we want to track out of the
  state."
  (let [view (get-in state state/current-view-path)
        user (get-in state state/user-login-path) 
        project (get-in state state/project-path)
        org (get-in state state/org-name-path)]
    (-> {:user user
         :view view}
        (merge (if project
                 {:repo (project-model/repo-name project)
                  :org (project-model/org-name project)}
                 {:org org
                  :repo nil})))))

(defn- get-properties-from-owner [owner]
  "Get a dict of the mutable properties we want to track out of the
  owner."
  (let [app-state @(om/get-shared owner [:_app-state-do-not-use])]
    (get-properties-from-state app-state)))

(defn- add-owner-properties-to-props [props owner]
  "Fill in any unsuppplied property values with those supplied
  in the app state via the owner."
  (-> owner
      (get-properties-from-owner)
      (merge props)))

(defn- add-state-properties-to-props [props state]
  "Fill in any unsuppplied property values with those supplied
  in the app state."
  (-> state
      (get-properties-from-state)
      (merge props)))

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
(defmulti track (fn [data]
                  (when (frontend.config/analytics-enabled?)
                    (cond
                      (supported-click-and-impression-events (:event-type data)) :track-click-and-impression-event
                      (supported-api-response-events (:event-type data)) :track-api-response-events
                      :else (:event-type data)))))

(defmethod track :default [data]
  (if (frontend.config/analytics-enabled?)
    (log-in-dev "Cannot log an unsupported event type, please add it to the list of supported events")
    (log-in-dev "Analytics are currently not enabled")))

(s/defmethod track :track-click-and-impression-event [event-data :- AnalyticsEvent]
  (let [{:keys [event-type properties owner]} event-data]
    (segment/track-event (name event-type) (add-owner-properties-to-props properties owner))))

;; Gotta finish this + add schema event
(s/defmethod track :track-api-response-events [event-data :- AnalyticsEventForControllers]
  (let [{:keys [event-type properties current-state]} event-data]
    (segment/track-event (name event-type) (add-state-properties-to-props properties current-state))))

(s/defmethod track :new-plan-created [event-data :- AnalyticsEvent]
  (let [{:keys [event-type properties owner]} event-data] 
    (segment/track-event "new-plan-created" (add-owner-properties-to-props properties owner))
    (intercom/track :paid-for-plan)))

(s/defmethod track :external-click [event-data :- ExternalClickEvent]
  (let [{:keys [event properties owner]} event-data]
    (segment/track-external-click event (add-owner-properties-to-props properties owner))))

;; Do we clear state before/after navigation? Can I trust including state?
;; What does it mean to include state?
(s/defmethod track :pageview [event-data :- PageviewEvent]
  (let [{:keys [navigation-point properties current-state]} event-data]
    (segment/track-pageview (name navigation-point) (add-state-properties-to-props properties current-state))))

(s/defmethod track :build-triggered [event-data :- BuildEvent]
  (let [{:keys [build properties owner]} event-data
        props (merge {:project (vcs-url/project-name (:vcs_url build))
                      :build-num (:build_num build)
                      :retry? true}
                     properties)]
    (segment/track-event "build-triggered" (add-owner-properties-to-props props owner))))

(s/defmethod track :view-build [event-data :- ViewBuildEvent]
  (let [{:keys [build user properties owner]} event-data
        props (merge (build-properties build) properties)]
    (segment/track-event "view-build" (add-owner-properties-to-props props owner))
    (when (and (:oss build) (build-model/owner? build user))
      (intercom/track :viewed-self-triggered-oss-build
                      {:vcs-url (vcs-url/project-name (:vcs_url build))
                       :outcome (:outcome build)}))))

(defmethod track :init-user [{:keys [login]}]
  (utils/swallow-errors
   (rollbar/init-user login)))
