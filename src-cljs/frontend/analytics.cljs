(ns frontend.analytics
  (:require-macros [frontend.analytics :refer [deftrack]])
  (:require [frontend.analytics.adroll :as adroll]
            [frontend.analytics.google :as google]
            [frontend.analytics.marketo :as marketo]
            [frontend.analytics.mixpanel :as mixpanel]
            [frontend.analytics.perfect-audience :as pa]
            [frontend.analytics.rollbar :as rollbar]
            [frontend.analytics.twitter :as twitter]
            [frontend.analytics.facebook :as facebook]
            [frontend.models.build :as build-model]
            [frontend.utils :as utils :include-macros true]
            [frontend.intercom :as intercom]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.style]
            [goog.string :as gstr]))

(deftrack init-user [login]
  (utils/swallow-errors
   (mixpanel/init-user login)
   (rollbar/init-user login)))

(deftrack set-existing-user []
  (mixpanel/set-existing-user))

(deftrack track-dashboard []
  (mixpanel/track "Dashboard")
  (google/track-pageview "/dashboard"))

(deftrack track-homepage []
  (utils/swallow-errors
   (mixpanel/track "Outer Home Page" {"window height" (.-innerHeight js/window)})
   (google/track-pageview "/homepage")))

(deftrack track-org-settings [org-name]
  (mixpanel/track "View Org" {:username org-name}))

(deftrack track-build [user build]
  (mixpanel/track "View Build" (merge {:running (build-model/running? build)
                                       :build-num (:build_num build)
                                       :vcs-url (vcs-url/project-name (:vcs_url build))
                                       :oss (boolean (:oss build))
                                       :outcome (:outcome build)}
                                      (when (:stop_time build)
                                        {:elapsed_hours (/ (- (.getTime (js/Date.))
                                                              (.getTime (js/Date. (:stop_time build))))
                                                           1000 60 60)})))
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

(deftrack track-collapse-nav []
  (mixpanel/track "aside_nav_collapsed"))

(deftrack track-expand-nav []
  (mixpanel/track "aside_nav_expanded"))

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

(deftrack track-trigger-build [build & {:keys [clear-cache? ssh?] :as extra}]
  (mixpanel/track "Trigger Build" (merge {:vcs-url (vcs-url/project-name (:vcs_url build))
                                          :build-num (:build_num build)
                                          :retry? true}
                                         extra)))

(deftrack track-follow-project []
  (google/track-event "Projects" "Add"))

(deftrack track-unfollow-project []
  (google/track-event "Projects" "Remove"))

(deftrack track-follow-repo []
  (google/track-event "Repos" "Add"))

(deftrack track-unfollow-repo []
  (google/track-event "Repos" "Remove"))

(deftrack track-message [message]
  (mixpanel/track-message message))

(deftrack track-view-page [zone]
  (mixpanel/track "View Page" {:zone zone :title js/document.title :url js/location.href}))

(deftrack track-link-clicked [target]
  (mixpanel/track "Track Link Clicked" {:link-class target}))

(deftrack utm? [[key val]]
  (gstr/startsWith (name key) "utm"))

(deftrack register-last-touch-utm [query-params]
  (mixpanel/register (->> query-params
                          (filter utm?)
                          (map (fn [[key val]] [(str "last_" (name key)) val]))
                          (into {}))))

(deftrack track-invitations [invitees context]
  (mixpanel/track "Sent invitations" (merge {:users (map :login invitees)}
                                            context))
  (doseq [u invitees]
    (mixpanel/track "Sent invitation" (merge {:login (:login u)
                                              :id (:id u)
                                              :email (:email u)}
                                             context))))

(deftrack track-invitation-prompt [context]
  (mixpanel/track "Saw invitations prompt" {:first_green_build true
                                            :project (:project-name context)}))

(deftrack managed-track [event properties]
  (mixpanel/managed-track event properties))

(deftrack track-test-stack [tab]
  (mixpanel/track "Test Stack" {:tab (name tab)}))

(deftrack track-ab-choices [choices]
  (let [mixpanel-choices (reduce (fn [m-choices [key value]]
                                   ;; rename keys from :test-name to "ab_test-name"
                                   (assoc m-choices (str "ab_" (name key)) value))
                                 {} choices)]
    (mixpanel/register-once mixpanel-choices)))
