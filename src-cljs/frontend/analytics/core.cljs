(ns frontend.analytics.core
  (:require [frontend.analytics.segment :as segment]
            [frontend.analytics.common :as common-analytics]
            [frontend.models.build :as build-model]
            [frontend.models.project :as project-model]
            [frontend.utils :refer [merror]]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.build :as build-util]
            [frontend.intercom :as intercom]
            [frontend.utils.vcs-url :as vcs-url]
            [schema.core :as s]
            [om.core :as om :include-macros true]
            [goog.style]
            [goog.string :as gstr]))

;; Below are the lists of our supported events.
;; Events should NOT be view specific. They should be view agnostic and
;; include a view in the properties.
;; Add new events here and keep each list of event types sorted alphabetically
(def supported-click-and-impression-events
  ;; These are the click and impression events.
  ;; They are in the fomat <item>-<clicked or impression>.
  #{:add-more-containers-clicked
    :authorize-vcs-clicked
    :beta-accept-terms-clicked
    :beta-join-clicked
    :beta-leave-clicked
    :branch-clicked
    :build-insights-upsell-clicked
    :build-insights-upsell-impression
    :build-timing-upsell-clicked
    :build-timing-upsell-impression
    :build-link-clicked
    :change-image-clicked
    :cancel-plan-clicked
    :dismiss-trial-offer-banner-clicked
    :insights-bar-clicked
    :invite-teammates-impression
    :invite-teammates-dismissed
    :invite-teammates-select-all-clicked
    :invite-teammates-select-none-clicked
    :login-clicked
    :new-plan-clicked
    :no-plan-banner-impression
    :parallelism-clicked
    :pr-link-clicked
    :project-clicked
    :project-settings-clicked
    :revision-link-clicked
    :select-plan-clicked
    :set-up-junit-clicked
    :signup-clicked
    :signup-impression
    :start-trial-clicked
    :teammates-invited
    :trial-offer-banner-impression
    :update-parallelism-clicked
    :update-plan-clicked
    :org-clicked
    :add-project-clicked
    :invite-teammates-clicked

    :web-notifications-permissions-banner-impression
    :web-notifications-permissions-set
    :web-notifications-permissions-banner-dismissed
    :account-settings-clicked})

(def supported-controller-events
  ;; TODO: All these events should be server side
  ;; These are the api response events.
  ;; They are in the format of <object>-<action take in the past tense>
  #{:project-branch-changed
    :project-builds-stopped
    :project-followed
    :project-unfollowed})

(def SupportedEvents
  (apply s/enum
         (concat supported-click-and-impression-events
                 supported-controller-events)))

(def CoreAnalyticsEvent
  {:event-type s/Keyword
   :current-state {s/Any s/Any}
   (s/optional-key :properties) (s/maybe {s/Keyword s/Any})})

(defn analytics-event-schema
  ([] (analytics-event-schema {}))
  ([schema]
   (merge CoreAnalyticsEvent schema)))

(def AnalyticsEvent
  (analytics-event-schema {:event-type SupportedEvents}))

(def ExternalClickEvent
  (analytics-event-schema {:event SupportedEvents}))

(def PageviewEvent
  (analytics-event-schema {:navigation-point s/Keyword}))

(def BuildEvent
  (analytics-event-schema {:build {s/Keyword s/Any}}))

(defn- add-properties-to-track-from-state [current-state]
  "Get a map of the mutable properties we want to track out of the
  state. Also add a timestamp."
  {:user (get-in current-state state/user-login-path) 
   :view (get-in current-state state/current-view-path)
   :repo (get-in current-state state/navigation-repo-path)
   :org (get-in current-state state/navigation-org-path)})

(defn- supplement-tracking-properties [{:keys [properties current-state]}]
  "Fill in any unsuppplied property values with those supplied
  in the current app state."
  (-> current-state
      (add-properties-to-track-from-state)
      (merge properties)))

(defn- current-subpage
  "Get the subpage for a pageview. If there is a subpage as well as a tab, the subpage
  takes preference since it is a step higher in the UI'f information hierarchy."
  [current-state]
  (or (get-in current-state state/navigation-subpage-path)
      (get-in current-state state/navigation-tab-path)
      :default))

(defn- current-build-tab
  "Get the tab for a build."
  [build current-state]
  (let [subpage (current-subpage current-state)]
    (if-not (= :default subpage)
      subpage
      (build-util/default-tab build (get-in current-state state/project-scopes-path)))))

(defn build-properties [build current-state]
  (merge {:running (build-model/running? build)
          :tab (current-build-tab build current-state)
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
                    (:event-type data))))

(s/defmethod track :default [event-data :- AnalyticsEvent]
  (let [{:keys [event-type properties current-state]} event-data]
    (segment/track-event event-type (supplement-tracking-properties {:properties properties
                                                                     :current-state current-state}))))

(s/defmethod track :external-click [event-data :- ExternalClickEvent]
  (let [{:keys [event properties current-state]} event-data]
    (segment/track-external-click event (supplement-tracking-properties {:properties properties
                                                                         :current-state current-state}))))

(s/defmethod track :pageview [event-data :- PageviewEvent]
  (let [{:keys [navigation-point properties current-state]} event-data]
    (segment/track-pageview navigation-point
                            (current-subpage current-state)
                            (supplement-tracking-properties {:properties properties
                                                             :current-state current-state}))))

(s/defmethod track :build-triggered [event-data :- BuildEvent]
  (let [{:keys [build properties current-state]} event-data
        props (merge {:project (vcs-url/project-name (:vcs_url build))
                      :build-num (:build_num build)
                      :retry? true}
                     properties)]
    (segment/track-event :build-triggered (supplement-tracking-properties {:properties props
                                                                           :current-state current-state}))))

(s/defmethod track :view-build [event-data :- BuildEvent]
  (let [{:keys [build properties current-state]} event-data
        props (merge (build-properties build current-state) properties)]
    (segment/track-event :view-build (supplement-tracking-properties {:properties props
                                                                      :current-state current-state}))))

(defn- get-user-properties-from-state [current-state]
  (let [analytics-id (get-in current-state state/user-analytics-id-path)
        user-data (get-in current-state state/user-path)]
    {:id analytics-id
     :user-properties (select-keys user-data (keys common-analytics/UserProperties))}))

(s/defmethod track :init-user [event-data :- CoreAnalyticsEvent]
  (segment/identify (get-user-properties-from-state (:current-state event-data))))
