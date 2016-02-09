(ns frontend.analytics
  (:require-macros [frontend.analytics :refer [deftrack]])
  (:require [frontend.analytics.adroll :as adroll]
            [frontend.analytics.google :as google]
            [frontend.analytics.perfect-audience :as pa]
            [frontend.analytics.rollbar :as rollbar]
            [frontend.analytics.mixpanel :as mixpanel]
            [frontend.analytics.twitter :as twitter]
            [frontend.analytics.facebook :as facebook]
            [frontend.analytics.segment :as segment]
            [frontend.models.build :as build-model]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.intercom :as intercom]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.style]
            [goog.string :as gstr]))

(deftrack init-user [login]
  (utils/swallow-errors
   (rollbar/init-user login)))

(deftrack set-existing-user []
  (mixpanel/set-existing-user))

(deftrack track-dashboard []
  (google/track-pageview "/dashboard"))

(deftrack track-homepage []
  (utils/swallow-errors
   (google/track-pageview "/homepage")))

(defn build-properties [build]
  (merge {:running (build-model/running? build)
          :build-num (:build_num build)
          :vcs-url (vcs-url/project-name (:vcs_url build))
          :oss (boolean (:oss build))
          :outcome (:outcome build)}
         (when (:stop_time build)
           {:elapsed_hours (/ (- (.getTime (js/Date.))
                                 (.getTime (js/Date. (:stop_time build))))
                              1000 60 60)})))

(deftrack track-build [user build]
  (mixpanel/track "View Build" (build-properties build))
  (when (and (:oss build) (build-model/owner? build user))
    (intercom/track :viewed-self-triggered-oss-build
                    {:vcs-url (vcs-url/project-name (:vcs_url build))
                     :outcome (:outcome build)})))

(deftrack track-path [path]
  (mixpanel/track-pageview path)
  (google/push path))

(deftrack track-page [page & [props]]
  (mixpanel/track page props))

(deftrack track-pricing []
  (mixpanel/register-once {:view-pricing true}))

(deftrack track-invited-by [invited-by]
  (mixpanel/register-once {:invited_by invited-by}))

(deftrack track-join-code [join-code]
  (when join-code (mixpanel/register-once {"join_code" join-code})))

(deftrack track-save-containers [upgraded?]
  (mixpanel/track "Save Containers")
  (if upgraded?
    (intercom/track :upgraded-containers)
    (intercom/track :downgraded-containers)))

(deftrack track-save-orgs []
  (mixpanel/track "Save Organizations"))

(deftrack track-signup []
  (utils/swallow-errors
   (twitter/track-signup)
   (facebook/track-signup)
   ((aget js/window "track_signup_conversion"))))

(deftrack track-payer [login]
  (mixpanel/track "Paid")
  (intercom/track :paid-for-plan)
  (pa/track "payer" {:orderId login})
  (twitter/track-payer)
  (adroll/record-payer))

(deftrack track-trigger-build [build & {:as extra}]
  (mixpanel/track "Trigger Build" (merge {:vcs-url (vcs-url/project-name (:vcs_url build))
                                          :build-num (:build_num build)
                                          :retry? true}
                                         extra)))

(deftrack track-follow-project []
  (google/track-event "Projects" "Add"))

(deftrack track-unfollow-project []
  (google/track-event "Projects" "Remove"))

(deftrack track-stop-building-project []
  (google/track-event "Projects" "Stop Building"))

(deftrack track-follow-repo []
  (google/track-event "Repos" "Add"))

(deftrack track-unfollow-repo []
  (google/track-event "Repos" "Remove"))

(defmulti tracking-properties (fn [message args state] message))
(defmethod tracking-properties :default [_ _ _] {})

(def ignored-control-messages #{:edited-input :toggled-input :clear-inputs})

(defn page-properties []
  {:url  js/location.href
   :title js/document.title})

(defmethod tracking-properties :support-dialog-raised [_ args state]
  (page-properties))

(defmethod tracking-properties :report-build-clicked [_ args state]
  (merge (page-properties)
         (build-properties (get-in @state state/build-path))))

(defmethod tracking-properties :collapse-build-diagnostics-toggled [_ {:keys [project-id-hash]} state]
  ;; Include whether the toggle collapsed the build diagnostics (true) or to expanded them (false).
  {:collapsed (get-in @state (state/project-build-diagnostics-collapsed-path project-id-hash))})

(deftrack track-pageview [navigation-point]
  (segment/track-pageview navigation-point))

(deftrack utm? [[key val]]
  (gstr/startsWith (name key) "utm"))

(deftrack register-last-touch-utm [query-params]
  (mixpanel/register (->> query-params
                          (filter utm?)
                          (map (fn [[key val]] [(str "last_" (name key)) val]))
                          (into {}))))

(deftrack managed-track [event properties]
  (segment/managed-track event properties))

(deftrack track* [event properties]
  (segment/track event properties))

(defn track
  "Simple passthrough to segment/track. Defined in terms of track* because
  deftrack doesn't handle multiple arities, and can't without a whole lot of
  effort."
  ([event] (track* event {}))
  ([event properties] (track* event properties)))

(deftrack track-test-stack [tab]
  (mixpanel/track "Test Stack" {:tab (name tab)}))

(deftrack track-ab-choices [choices]
  (let [mixpanel-choices (reduce (fn [m-choices [key value]]
                                   ;; rename keys from :test-name to "ab_test-name"
                                   (assoc m-choices (str "ab_" (name key)) value))
                                 {} choices)]
    (mixpanel/register-once mixpanel-choices)))

(deftrack track-signup-click [data]
  (segment/track-event "signup-click" data))

(deftrack track-signup-impression [data]
  (segment/track-event "signup-impression" data))

(deftrack track-build-insights-upsell-impression [data]
  (segment/track-event "build-insights-upsell-impression" data))

(deftrack track-build-insights-upsell-click [data]
  (segment/track-event "build-insights-upsell-click" data))

(deftrack track-build-timing-upsell-impression [data]
  (segment/track-event "build-timing-upsell-impression" data))

(deftrack track-build-timing-upsell-click [data]
  (segment/track-event "build-timing-upsell-click" data))

(deftrack track-build-tests-ad-click [data]
  (segment/track-event "build-tests-ad-click" data))

(deftrack track-beta-join-click [data]
  (segment/track-event "beta-join-click" data))

(deftrack track-beta-terms-accept [data]
  (segment/track-event "beta-terms-accept" data))

(deftrack track-beta-leave-click [data]
  (segment/track-event "beta-leave-click" data))

(deftrack track-parallelism-build-header-click [data]
  (segment/track-event "parallelism-build-header-click" data))

(deftrack track-cancel-button-clicked [data]
  (segment/track-event "cancel-button-clicked" data))
