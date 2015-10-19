(ns frontend.components.project.common
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.components.forms :as forms]
            [frontend.datetime :as time-utils]
            [frontend.models.plan :as plan-model]
            [frontend.models.user :as user-model]
            [frontend.models.project :as project-model]
            [frontend.routes :as routes]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.vcs-url :as vcs-url]
            [cljs-time.format :as time-format]
            [goog.string :as gstring]
            [goog.string.format]
            [inflections.core :refer (pluralize)]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn show-trial-notice? [project plan]
  (let [conditions [(not (project-model/oss? project))
                    (plan-model/trial? plan)
                    ;; only bug them if < 20 days left in trial
                    ;; note that this includes expired trials
                    (< (plan-model/days-left-in-trial plan) 20)

                    ;; only show freemium trial notices if the
                    ;; trial is still active.
                    (if (plan-model/freemium? plan)
                      (not (plan-model/trial-over? plan))
                      true)]]
    (utils/mlog (gstring/format "show-trial-notice? has conditions %s days left %d"
                                conditions (plan-model/days-left-in-trial plan)))
    (every? identity conditions)))

(defn non-freemium-trial-html [plan project project-name days org-name plan-path]
  (html
   [:div.alert {:class (when (plan-model/trial-over? plan) "alert-error")}
    (cond (plan-model/trial-over? plan)
          (list (gstring/format "The %s project is covered by %s's plan, whose trial ended %s ago. "
                                project-name org-name (pluralize (Math/abs days) "day"))
                [:a {:href plan-path} "Add a plan to continue running builds of private repositories"]
                ".")

          (> days 10)
          (list (gstring/format "The %s project is covered by %s's trial, enjoy! (or check out "
                                project-name org-name)
                [:a {:href plan-path} "our plans"]
                ").")

          (> days 7)
          (list (gstring/format "The %s project is covered by %s's trial which has %s left. "
                                project-name org-name (pluralize days "day"))
                [:a {:href plan-path} "Check out our plans"]
                ".")

          (> days 4)
          (list (gstring/format "The %s project is covered by %s's trial which has %s left. "
                                project-name org-name (pluralize days "day"))
                [:a {:href plan-path} "Add a plan"]
                " to keep running builds.")

          :else
          (list (gstring/format "The %s project is covered by %s's trial which expires in %s! "
                                project-name org-name (plan-model/pretty-trial-time plan))
                [:a {:href plan-path} "Add a plan"]
                " to keep running builds."))]))

;; TODO: figure out where this is being tracked in mixpanel and launch one of these.
(defn freemium-trial-html [plan project project-name days org-name plan-path]
  (html
    [:div.alert {:class "alert-success"}
       (list (gstring/format "This project is covered by %s's trial of %s containers which expires in %s. "
                             org-name (plan-model/usable-containers plan) (pluralize days "day"))
             [:a.pay-now-plain-text {:href plan-path} "Please enter your payment information"]
             " before the trial expires to continue using these containers."
             )]))

(defn freemium-trial-html-b [plan project project-name days org-name plan-path]
  (html
    [:div.alert {:class "alert-success"}
       (list (gstring/format "This project is covered by %s's trial of %s containers which expires in %s. "
                             org-name (plan-model/usable-containers plan) (pluralize days "day"))
             "Please enter your payment information before the trial expires to continue using these containers.    "
             [:a.pay-now-button {:href plan-path}
              [:button "Pay Now"]]
             )]))

(defn trial-notice [data owner]
  (reify
    om/IRender
    (render [_]
      (let [plan (:plan data)
            project (:project data)
            project-name (gstring/format "%s/%s" (:username project) (:reponame project))
            days (plan-model/days-left-in-trial plan)
            org-name (:org_name plan)
            plan-path (routes/v1-org-settings-subpage {:org org-name :subpage "containers"})
            trial-notice-fn (if (plan-model/freemium? plan)
                              (if (om/get-shared owner [:ab-tests :pay_now_button])
                                freemium-trial-html-b
                                freemium-trial-html)
                              non-freemium-trial-html)]
        (trial-notice-fn plan project project-name days org-name plan-path)))))

(defn show-enable-notice [project]
  (not (:has_usable_key project)))

(defn enable-notice [project owner]
  (reify
    om/IRender
    (render [_]
      (let [project-name (vcs-url/project-name (:vcs_url project))
            project-id (project-model/id project)]
        (html
         [:div.row-fluid
          [:div.offset1.span10
           [:div.alert.alert-error
            "Project "
            project-name
            " isn't configured with a deploy key or a github user, so we may not be able to test all pushes."
            (forms/managed-button
             [:button.btn.btn-primary
              {:data-loading-text "Adding...",
               :on-click #(raise! owner [:enabled-project {:project-id project-id
                                                           :project-name project-name}])}
              "Add SSH key"])]]])))))

(defn show-follow-notice [project]
  ;; followed here indicates that the user is following this project, not that the
  ;; project has followers
  (not (:followed project)))

(defn follow-notice [project owner]
  (reify
    om/IRender
    (render [_]
      (let [project-name (vcs-url/project-name (:vcs_url project))
            vcs-url (:vcs_url project)]
        (html
         [:div.row-fluid
          [:div.offset1.span10
           [:div.alert.alert-success
            (forms/managed-button
             [:button.btn.btn-primary
              {:data-loading-text "Following...",
               :on-click #(raise! owner [:followed-repo {:vcs_url vcs-url}])}
              "Follow"])
            " " project-name " to add " project-name " to your sidebar and get build notifications."]]])))))

(def email-prefs
  [["default" "Default"]
   ["all" "All builds"]
   ["smart" "My breaks and fixes"]
   ["none" "None"]])

(defn email-pref [{:keys [project user]} owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [vcs_url]} project
            prefs (user-model/project-preferences user)
            pref (get-in prefs [vcs_url :emails] "default")
            ch (om/get-shared owner [:comms :controls])]
        (html
          [:div
           [:h3 (project-model/project-name project)]
           [:select {:value pref
                     :on-change #(let [value (.. % -target -value)
                                       args {vcs_url {:emails value}}]
                                   (raise! owner [:project-preferences-updated args]))}
            (for [[pref label] email-prefs]
              [:option {:value pref} label])]])))))

(defn suspended-notice [plan owner]
  (reify
    om/IRender
    (render [_]
      (let [org-name (:org_name plan)
            plan-path (routes/v1-org-settings-subpage {:org org-name :subpage "billing"})]
        (html
         [:div.alert.alert-danger.suspended-notice
          (list org-name
                "'s plan hasn't been paid for! "
                (if (plan-model/admin? plan)
                  (list "Please " [:a {:href plan-path} "update its billing info "]
                        " in order to restore paid containers.")
                  (list "Please ask an administrator to update its billing info."
                        " in order to restore paid containers.")))])))))
