(ns frontend.components.project.common
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.components.forms :as forms]
            [frontend.config :as config]
            [frontend.datetime :as time-utils]
            [frontend.models.feature :as feature]
            [frontend.models.plan :as plan-model]
            [frontend.models.user :as user-model]
            [frontend.models.project :as project-model]
            [frontend.routes :as routes]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils.html :refer [open-ext]]
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

(defn freemium-trial-html [plan project project-name days org-name plan-path]
  (html
    [:div.alert {:class "alert-success"}
       (list (gstring/format "This project is covered by %s's trial of %s containers which expires in %s. "
                             org-name (plan-model/usable-containers plan) (pluralize days "day"))
             [:a.pay-now-plain-text {:href plan-path} "Update your plan"]
             " before the trial expires to continue using these containers."
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
            plan-path (routes/v1-org-settings-path {:org org-name
                                                    :vcs_type (:vcs_type project)
                                                    :_fragment "containers"})
            trial-notice-fn (if (plan-model/freemium? plan)
                                freemium-trial-html
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

(def email-prefs
  [["default" "Default"]
   ["all" "All builds"]
   ["smart" "Branches I've pushed to"]
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
          [:div.email-pref
           [:div (project-model/project-name project)]
           [:select.form-control {:value pref
                                  :on-change #(let [value (.. % -target -value)
                                                    args {vcs_url {:emails value}}]
                                                (raise! owner [:project-preferences-updated args]))}
            (for [[pref label] email-prefs]
              [:option {:value pref} label])]])))))

(defn suspended-notice [{:keys [plan vcs_type]} owner]
  (reify
    om/IRender
    (render [_]
      (let [org-name (:org_name plan)
            plan-path (routes/v1-org-settings-path {:org org-name
                                                    :vcs_type vcs_type
                                                    :_fragment "billing"})]
        (html
         [:div.alert.alert-danger.suspended-notice
          (list org-name
                "'s plan hasn't been paid for! "
                (if (plan-model/admin? plan)
                  (list "Please " [:a {:href plan-path} "update its billing info "]
                        " in order to restore paid containers.")
                  (list "Please ask an administrator to update its billing info."
                        " in order to restore paid containers.")))])))))

(defn mini-parallelism-faq [project-data]
  [:div.mini-faq
   [:div.mini-faq-item
    [:h3 "What are containers?"]
    [:p
     "Containers are what we call the virtual machines that your tests run in. "
     (if (config/enterprise?)
       (list
        "You currently have "
        (get-in project-data [:plan :containers])
        " containers and can use up to "
        (plan-model/max-parallelism (:plan project-data))
        "x parallelism.")
       (list
        "Your current plan has "
        (get-in project-data [:plan :containers])
        " containers and supports up to "
        (plan-model/max-parallelism (:plan project-data))
        "x parallelism."))]

    [:p "With 16 containers you could run:"]
    [:ul
     [:li "16 simultaneous builds at 1x parallelism"]
     [:li "8 simultaneous builds at 2x parallelism"]
     [:li "4 simultaneous builds at 4x parallelism"]
     [:li "2 simultaneous builds at 8x parallelism"]
     [:li "1 build at 16x parallelism"]]]
   [:div.mini-faq-item
    [:h3 "What is parallelism?"]
    [:p
     "We split your tests into groups, and run each group on different machines in parallel. This allows them run in a fraction of the time, for example:"]
    [:p]
    [:ul
     [:li "a 45 minute build fell to 18 minutes with 3x build speed,"]
     [:li
      "a 20 minute build dropped to 11 minutes with 2x build speed."]]
    [:p
     "Each machine is completely separated (sandboxed and firewalled) from the others, so that your tests can't conflict with each other: separate databases, file systems, process space, and memory."]
    [:p
     "For RSpec, Cucumber and Test::Unit, we'll automatically run your tests, splitting them appropriately among different machines. If you have a different test suite, you can "
     [:a
      (open-ext {:href "/docs/parallel-manual-setup"})
      "control the parallelism directly"]
     "."]]
   (when-not (config/enterprise?)
     [:div.mini-faq-item
      [:h3 "What do others think?"]
      [:blockquote
       [:i
        "The thing that sold us on Circle was the speed. Their tests run really really fast. We've never seen that before. One of our developers just pushes to branches so that Circle will run his tests, instead of testing on his laptop. The parallelization just works - we didn't have to tweak anything. Amazing service."]]
      [:ul
       [:li [:a {:href "http://zencoder.com/company/"} "Brandon Arbini"]]
       [:li [:a {:href "http://zencoder.com/"} "Zencoder.com"]]]])])
