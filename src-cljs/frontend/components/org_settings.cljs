(ns frontend.components.org-settings
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.set]
            [frontend.async :refer [raise!]]
            [frontend.routes :as routes]
            [frontend.datetime :as datetime]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [frontend.analytics :as analytics]
            [frontend.models.organization :as org-model]
            [frontend.models.plan :as pm]
            [frontend.models.repo :as repo-model]
            [frontend.models.user :as user-model]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.inputs :as inputs]
            [frontend.components.plans :as plans-component]
            [frontend.components.shared :as shared]
            [frontend.components.project.common :as project-common]
            [frontend.config :as config]
            [frontend.state :as state]
            [frontend.stripe :as stripe]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.state :as state-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format]
            [inflections.core :as infl :refer [pluralize]]
            [frontend.models.feature :as feature])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [html]]))

(defn non-admin-plan [{:keys [org-name login]} owner]
  (reify
    om/IRender
    (render [_]
      (html [:div.row-fluid.plans
             [:div.span12
              [:h3
               "Do you want to create a plan for an organization that you don't admin?"]
              [:ol
               [:li
                "Sign up for a plan from your "
                [:a {:href (routes/v1-org-settings-subpage {:org login
                                                            :subpage "containers"})}
                 "\"personal organization\" page"]]
               [:li
                "Add " org-name
                " to the list of organizations you pay for or transfer the plan to "
                org-name " from the "
                [:a {:href (routes/v1-org-settings-subpage {:org login
                                                            :subpage "organizations"})}
                 "plan's organization page"]
                "."]]]]))))

(defn users [app owner]
  (reify
    om/IRender
    (render [_]
      (let [users (get-in app state/org-users-path)
            projects (get-in app state/org-projects-path)
            org-name (get-in app state/org-name-path)
            projects-by-follower (org-model/projects-by-follower projects)
            sorted-users (sort-by (fn [u]
                                    (- (count (get projects-by-follower (:login u)))))
                                  users)]
        (html
         [:div.users
          [:h1
           "CircleCI users in the " org-name " organization"]
          [:div
           (if-not (seq users)
             [:h4 "No users found."])
           [:div
            (for [user sorted-users
                  :let [login (:login user)
                        followed-projects (get projects-by-follower login)]]
              [:div.well.om-org-user
               {:class (if (zero? (count followed-projects))
                         "fail"
                         "success")}

               [:div.om-org-user-projects-container
                [:div.om-org-user-projects
                 [:h3.heading
                  [:img.gravatar {:src (gh-utils/make-avatar-url user :size 60)}]
                  (if (seq followed-projects)
                    (str login " is following:")
                    (str login " is not following any " org-name  " projects"))]
                 (for [project (sort-by (fn [p] (- (count (:followers p)))) followed-projects)
                       :let [vcs-url (:vcs_url project)]]
                   [:div.om-org-user-project
                    [:a {:href (routes/v1-project-dashboard {:org (vcs-url/org-name vcs-url)
                                                             :repo (vcs-url/repo-name vcs-url)})}
                     (vcs-url/project-name vcs-url)]])]]])]]])))))

(defn followers-container [followers owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.followers-container
        [:div.row-fluid
         (for [follower followers]
           [:span.follower-container
            {:style {:display "inline-block"}
             :title (:login follower)
             :data-toggle "tooltip"
             :data-placement "right"}
            [:img.gravatar
             {:src (gh-utils/make-avatar-url follower :size 60)}]
            " "
            [:span (:login follower)]])]]))))

(defn projects [app owner]
  (reify
    om/IRender
    (render [_]
      (let [users (get-in app state/org-users-path)
            projects (get-in app state/org-projects-path)
            {followed-projects true unfollowed-projects false} (group-by #(pos? (count (:followers %)))
                                                                         projects)
            org-name (get-in app state/org-name-path)]
        (html
         [:div
          [:div.followed-projects.row-fluid
           [:h1 "Followed projects"]
           (if-not (seq followed-projects)
             [:h3 "No followed projects found."]

             [:div.span8
              (for [project followed-projects
                    :let [vcs-url (:vcs_url project)]]
                [:div.row-fluid
                 [:div.span12.well

                   [:div.project-header
                    [:span.project-name
                     [:a {:href (routes/v1-project-dashboard {:org (vcs-url/org-name vcs-url)
                                                              :repo (vcs-url/repo-name vcs-url)})}
                      (vcs-url/project-name vcs-url)]
                     " "]
                    [:div.github-icon
                     [:a {:href vcs-url}
                      [:i.octicon.octicon-mark-github]]]
                    [:div.settings-icon
                     [:a.edit-icon {:href (routes/v1-project-settings {:org (vcs-url/org-name vcs-url)
                                                                       :repo (vcs-url/repo-name vcs-url)})}
                      [:i.material-icons "settings"]]]]
                  (om/build followers-container (:followers project))]])])]
          [:div.row-fluid
           [:h1 "Untested repos"]
           (if-not (seq unfollowed-projects)
             [:h3 "No untested repos found."]

             [:div.span8
              (for [project unfollowed-projects
                    :let [vcs-url (:vcs_url project)]]
                [:div.row-fluid
                 [:div.span12.well

                   [:div.project-header
                    [:span.project-name
                     [:a {:href (routes/v1-project-dashboard {:org (vcs-url/org-name vcs-url)
                                                              :repo (vcs-url/repo-name vcs-url)})}
                      (vcs-url/project-name vcs-url)]
                     " "]
                    [:div.github-icon
                     [:a {:href vcs-url}
                      [:i.octicon.octicon-mark-github]]]
                    [:div.settings-icon
                     [:a.edit-icon {:href (routes/v1-project-settings {:org (vcs-url/org-name vcs-url)
                                                                       :repo (vcs-url/repo-name vcs-url)})}
                      [:i.material-icons "settings"]]]]
                  (om/build followers-container (:followers project))]])])]])))))

(defn plans-trial-notification [plan org-name owner]
  [:div.row-fluid
   [:div.alert.alert-success {:class (when (pm/trial-over? plan) "alert-error")}
    [:p
     (if (pm/trial-over? plan)
       "Your 2-week trial is over!"

       [:span "The " [:strong org-name] " organization has "
        (pm/pretty-trial-time plan) " left in its trial."])]
    [:p "Your trial is equivalent to a plan with" (pluralize (pm/trial-containers plan) "containers") "."]
    (when (and (not (:too_many_extensions plan))
               (> 3 (pm/days-left-in-trial plan)))
      [:p
       "Need more time to decide? "
       [:a {:href "mailto:sayhi@circleci.com"} "Get in touch."]])]])

(defn piggieback-plan-wording [plan]
  (let [containers (pm/paid-containers plan)]
    (str
      (when (pos? containers)
        (str containers " containers"))
      (when (and (pos? containers) (pm/osx? plan))
        " and ")
      (when (pm/osx? plan)
        (-> plan :osx :template :name)))))

(defn parent-plan-name [plan]
  [:em (:org_name plan)])

(defn plans-piggieback-plan-notification [plan current-org-name]
  [:div.row-fluid
   [:div.offset1.span10
    [:div.alert.alert-success
     [:p
      "This organization is covered under " (parent-plan-name plan) "'s plan which has " (piggieback-plan-wording plan)]
     [:p
      "If you're an admin in the " (parent-plan-name plan)
      " organization, then you can change plan settings from the "
      [:a {:href (routes/v1-org-settings {:org (:org_name plan)})}
       (:org_name plan) " plan page"] "."]
     [:p
      "You can create a separate plan for " [:em current-org-name] " when you're no longer covered by " (parent-plan-name plan) "."]]]])

(defn plural-multiples [num word]
  (if (> num 1)
    (pluralize num word)
    word))

(defn pluralize-no-val [num word]
  (if (= num 1) (infl/singular word) (infl/plural word)))

(defn osx-plan [{:keys [plan-type plan price current-plan]} owner]
  (reify
    om/IRender
    (render [_]
      (let [plan-type-key (keyword (str "osx-" plan-type))
            new-plan-fn #(do (raise! owner [:new-osx-plan-clicked
                                            {:plan-type {:template plan-type-key}
                                             :price price
                                             :description (str "OS X " (clojure.string/capitalize plan-type) " - $" price "/month.")}])
                             false)
            update-plan-fn #(do (raise! owner [:update-osx-plan-clicked {:plan-type {:template plan-type-key}}])
                                false)
            plan-selected? (= plan-type-key (keyword current-plan))
            plan-img     [:img {:src (utils/cdn-path (str "img/inner/" plan-type "-2x.png"))}]
            loading-img  [:img {:src (utils/cdn-path (str "img/inner/" plan-type "-loading-2x.png"))}]]
        (html
          (if (and (pm/osx? plan) (not (pm/osx-trial-plan? plan)))
            (if plan-selected?
              [:img.selected {:src (utils/cdn-path (str "img/inner/" plan-type "-selected-2x.png"))}]
              (forms/managed-button
                [:a.unselected
                 {:data-success-text plan-img
                  :data-loading-text loading-img
                  :data-failed-text plan-img
                  :on-click update-plan-fn}
                 plan-img]))
            (forms/managed-button
              [:a.unselected
               {:data-success-text plan-img
                :data-loading-text plan-img
                :data-failed-text plan-img
                :on-click new-plan-fn}
               plan-img])))))))

(def osx-faq-items
  [{:question (list
               [:p "This is a separate service? How will I be billed?"])
    :answer (list
             [:p "Yes! This is a standalone service. The charge will be pro-rated and added to your existing plan."]
             [:p (str
                  "The word \"container\" has been pretty vague in the engineering landscape and we’ve done nothing to help with that :). "
                  "There is a non-trivial difference regarding an iOS-capable container and a linux container "
                  "because any commercial provider needs to run iOS builds on Apple hardware.")]
             [:p (str "The underlying delivery system is very different, from our cost structure to our software tooling."
                      " Accordingly, we’re currently offering access primarily based on your concurrency needs (use of multiple containers).")])}
   {:question (list
               [:p "What is concurrency vs. parallelism?"])
    :answer (list
             [:p (str "In a linux setting, parallelism refers to splitting a build across multiple containers. "
                      "This is not currently a feature of iOS builds - however, there are still speed gains from concurrency.")]
             [:p "Concurrency refers to running multiple jobs at the same time (e.g., two jobs on two containers).  This is a feature of the iOS product."])}
   {:question (list
               [:p "Wait...in the beta, how did my access to containers work?"])
    :answer (list
             [:p (str "During the beta, we used your current plan on linux containers as a proxy to enable access to Apple machines. "
                      "It was just a shortcut on our side to help folks build as soon as possible and a thank-you to customers that pay for multiple linux containers.")])}
   {:question (list
               [:p "So what plan should I choose? / I’m concerned about how many minutes I have used?"])
    :answer (list
             [:p "If you have questions, please feel free reach out to "
              [:a {:href "mailto:sayhi@circleci.com"}
               "sayhi@circleci.com"]
              (str " and we’ll provide some guidance regarding your recent usage. "
                   "In the near future, we’ll display your usage within the app.")])}
   {:question (list
               [:p "What if I go over the minute limit?"])
    :answer (list
             [:p (str "Tiering is to help make sure we can stabilize capacity and offer competitive price points "
                      "which should hopefully lead to the greatest possible utility all around.")
              [:p (str "In this release, we will not charge overages. We may suspend accounts if minute usage far exceeds the plan limit. "
                       "We’ll have our team keeping an eye on usage and we will reach out proactively if you get close to your respective plan limit.")]
              [:p "In the near future, we’ll display your usage within the app."]])}
   {:question (list
               [:p "What if I don’t choose a plan?"])
    :answer (list
             [:p (str "Starting November 30th, organizations that have not chosen a plan may see degraded performance (as long as we have "
                      "capacity, we’ll do our best to build while you determine which plan works best for you!).")])}
   {:question (list
               [:p "What if we’re building open-source?"])
    :answer (list
             [:p "Please reach out to our team at "
              [:a {:href "mailto:sayhi@circleci.com"}
               "sayhi@circleci.com"]
              " - we’re happy to provide significant discounts for open-source projects!"])}])

(defn osx-faq [items owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:fieldset.osx-faq
        [:legend "FAQs"]
        [:dl
         (for [{:keys [question answer]} items]
           (list
            [:dt question]
            [:dd answer]))]]))))

(defn osx-plans [plan owner]
  (reify
    om/IRender
    (render [_]
      (let [current-plan (some-> plan :osx :template :id)]
        (html
          [:div.osx-plans
           [:fieldset
            [:legend (str "iOS Limited Release Plans")]
            [:p "Your selection selection below only applies to iOS service and will not affect Linux Containers above."]]
           [:div.plan-selection
            (om/build osx-plan {:plan plan :price 79 :plan-type "starter" :current-plan current-plan})
            (om/build osx-plan {:plan plan :price 139 :plan-type "standard" :current-plan current-plan})
            (om/build osx-plan {:plan plan :price 279 :plan-type "growth" :current-plan current-plan})
            [:a.unselected {:href "mailto:sayhi@circleci.com"}
             [:img {:src (utils/cdn-path "img/inner/mobile-focused-2x.png")}]]]])))))

(defn linux-plan [{:keys [app checkout-loaded?]} owner]
  (reify
    om/IRender
    (render [_]
      (let [org-name (get-in app state/org-name-path)
            plan (get-in app state/org-plan-path)
            selected-containers (or (get-in app state/selected-containers-path)
                                     (pm/paid-containers plan))
            login (get-in app state/user-login-path)
            view (get-in app state/current-view-path)
            min-slider-val 0
            max-slider-val (max 80 (* 2 (pm/paid-containers plan)))
            selected-paid-containers (max 0 selected-containers)
            osx-total (or (some-> plan :osx :template :price) 0)
            old-total (- (pm/stripe-cost plan) osx-total)
            new-total (pm/cost plan (+ selected-containers (pm/freemium-containers plan)))
            container-cost (pm/per-container-cost plan)
            piggiebacked? (pm/piggieback? plan org-name)
            button-clickable? (not= (if piggiebacked? 0 (pm/paid-containers plan))
                                    selected-paid-containers)]
      (html
        [:div#edit-plan {:class "pricing.page"}
         (when-not (config/enterprise?)
           [:fieldset
            [:legend "More containers means faster builds and lower queue times."]])
         [:div.main-content
          [:div
           [:legend "Linux Plan - "
            [:div.container-input
             [:input.form-control {:style {:margin "4px" :height "calc(2em + 2px)"}
                                   :type "text" :value selected-containers
                                   :on-change #(utils/edit-input owner state/selected-containers-path %
                                                                 :value (int (.. % -target -value)))}]
             [:span.new-plan-total (str "paid " (pluralize-no-val selected-containers "container")
                                        (when-not (config/enterprise?)
                                          (str (when-not (zero? new-total) (str " for $" new-total "/month"))))
                                        " + 1 free container")]]]
           [:form
            [:div.container-picker
             [:p (str "Our pricing is flexible and scales with you. Add as many containers as you want for $" container-cost "/month each.")]
             (om/build shared/styled-range-slider
                       (merge app {:start-val selected-containers :min-val min-slider-val :max-val max-slider-val}))]
            [:fieldset
             (if (and (pm/can-edit-plan? plan org-name) (or (config/enterprise?) (pm/paid? plan)))
               (forms/managed-button
                 (let [enterprise-text "Save changes"]
                   (if (and (zero? new-total) (not (config/enterprise?)))
                     [:a.btn.btn-large.btn-primary.cancel
                      {:href "#cancel"
                       :disabled (when-not button-clickable? "disabled")
                       :on-click #(analytics/track {:event-type :cancel-plan-clicked
                                                    :owner owner
                                                    :properties {:repo nil}})}
                      "Cancel plan"]
                     [:button.btn.btn-large.btn-primary.upgrade
                      {:data-success-text "Saved",
                       :data-loading-text "Saving...",
                       :type "submit"
                       :disabled (when-not button-clickable? "disabled")
                       :on-click (when button-clickable?
                                   #(do (raise! owner [:update-containers-clicked
                                                       {:containers selected-paid-containers}])
                                        false))}
                      (if (config/enterprise?)
                        enterprise-text
                        "Update plan")])))
               (if-not checkout-loaded?
                 [:div.loading-spinner common/spinner [:span "Loading Stripe checkout"]]
                 (forms/managed-button
                   [:button.btn.btn-lg.btn-success.center
                    {:data-success-text "Paid!",
                     :data-loading-text "Paying...",
                     :data-failed-text "Failed!",
                     :type "submit"
                     :disabled (when-not button-clickable? "disabled")
                     :on-click (when button-clickable?
                                 #(do (raise! owner [:new-plan-clicked
                                                     {:containers selected-paid-containers
                                                      :paid {:template (:id pm/default-template-properties)}
                                                      :price new-total
                                                      :description (str "$" new-total "/month, includes "
                                                                        (pluralize selected-containers "container"))}])
                                      false))}
                    "Pay Now"])))

             (when-not (config/enterprise?)
               ;; TODO: Clean up conditional here - super nested and many interactions
               (if (or (pm/paid? plan) (and (pm/freemium? plan) (not (pm/in-trial? plan))))
                 [:span.help-block
                  (cond
                    (< old-total new-total) "We'll charge your card today, for the prorated difference between your new and old plans."
                    (> old-total new-total) "We'll credit your account, for the prorated difference between your new and old plans.")]
                 (if (pm/in-trial? plan)
                   [:span "Your trial will end in " (pluralize (Math/abs (pm/days-left-in-trial plan)) "day")
                    "."]
                   ;; TODO: Only show for trial-plans?
                   [:span "Your trial of " (pluralize (pm/trial-containers plan) "container")
                    " ended " (pluralize (Math/abs (pm/days-left-in-trial plan)) "day")
                    " ago. Pay now to enable builds of private repositories."])))]]]]])))))

(defn containers [app owner]
  (reify
    ;; I stole the stateful "did we load stripe checkout code" stuff
    ;; from the plan component above, but the billing-card component
    ;; also has it. What's the nice way to
    ;; abstract it out?
    om/IInitState
    (init-state [_]
      {:checkout-loaded? (stripe/checkout-loaded?)
       :checkout-loaded-chan (chan)})
    om/IWillMount
    (will-mount [_]
      (let [ch (om/get-state owner [:checkout-loaded-chan])
            checkout-loaded? (om/get-state owner [:checkout-loaded?])]
        (when-not checkout-loaded?
          (go (<! ch)
              (utils/mlog "Stripe checkout loaded")
              (om/set-state! owner [:checkout-loaded?] true))
          (utils/mlog "Loading Stripe checkout")
          (stripe/load-checkout ch))))
    om/IDidMount
    (did-mount [_]
      (utils/tooltip "#grandfathered-tooltip-hack" {:animation false}))
    om/IWillUnmount
    (will-unmount [_]
      (close! (om/get-state owner [:checkout-loaded-chan])))
    om/IRenderState
    (render-state [_ {:keys [checkout-loaded?]}]
      (let [plan (get-in app state/org-plan-path)
            org-name (get-in app state/org-name-path)]
        (html
          (if-not plan
            (cond ;; TODO: fix; add plan
              (nil? plan)
                [:div.loading-spinner common/spinner]
              (not (seq plan))
                [:h3 (str "No plan exists for" org-name "yet. Follow a project to trigger plan creation.")]
              :else
                [:h3 "Something is wrong! Please submit a bug report."])

            (if (pm/piggieback? plan org-name)
              (plans-piggieback-plan-notification plan org-name)
              [:div
               (om/build linux-plan {:app app :checkout-loaded? checkout-loaded?})
               (if (and (feature/enabled? :osx-plans)
                        (get-in app state/org-osx-enabled-path))
                 (list
                   (om/build osx-plans plan)
                   (om/build osx-faq osx-faq-items))
                 (project-common/mini-parallelism-faq {}))])))))))

(defn piggyback-organizations [app owner]
  (om/component
   (html
    (let [org-name (get-in app state/org-name-path)
          user-login (:login (get-in app state/user-path))
          user-orgs (get-in app state/user-organizations-path)
          plan (get-in app state/org-plan-path)
          ;; orgs that this user can add to piggyback orgs
          elligible-piggyback-orgs (-> (map :login user-orgs)
                                       (set)
                                       (conj user-login)
                                       (disj org-name))
          ;; This lets users toggle selected piggyback orgs that are already in the plan. Merges:
          ;; (:piggieback_orgs plan): ["org-a" "org-b"] with
          ;; selected-orgs:           {"org-a" false "org-c" true}
          ;; to return #{"org-b" "org-c"}
          selected-piggyback-orgs (set (keys (filter last
                                                     (merge (zipmap (:piggieback_orgs plan) (repeat true))
                                                            (get-in app state/selected-piggyback-orgs-path)))))]
      [:div.row-fluid
       [:div.span8
        [:fieldset
         [:legend "Extra organizations"]
         [:p
          "Your plan covers all repositories (including forks) in the "
          [:strong org-name]
          " organization by default."]
         [:p "You can let any GitHub organization you belong to, including personal accounts, piggyback on your plan. Projects in your piggyback organizations will be able to run builds on your plan."]
         [:p
          [:span.label.label-info "Note:"]
          " Members of the piggyback organizations will be able to see that you're paying for them, the name of your plan, and the number of containers you've paid for. They won't be able to edit the plan unless they are also admins on the " org-name " org."]
         (if-not user-orgs
           [:div "Loading organization list..."]
           [:div.row-fluid
            [:div.span12
             [:form
              [:div.controls
               ;; orgs that this user can add to piggyback orgs and existing piggyback orgs
               (for [org (sort (clojure.set/union elligible-piggyback-orgs
                                                  (set (:piggieback_orgs plan))))]
                 [:div.checkbox
                  [:label
                   [:input
                    (let [checked? (contains? selected-piggyback-orgs org)]
                      {:value org
                       :checked checked?
                       :on-change #(utils/edit-input owner (conj state/selected-piggyback-orgs-path org) % :value (not checked?))
                       :type "checkbox"})]
                   org]])]
              [:div.form-actions.span7
               (forms/managed-button
                [:button.btn.btn-large.btn-primary
                 {:data-success-text "Saved",
                  :data-loading-text "Saving...",
                  :type "submit",
                  :on-click #(do (raise! owner [:save-piggyback-orgs-clicked {:org-name org-name
                                                                              :selected-piggyback-orgs selected-piggyback-orgs}])
                                 false)}
                 "Also pay for these organizations"])]]]])]]]))))

(defn transfer-organizations [app owner]
  (om/component
   (html
    (let [org-name (get-in app state/org-name-path)
          user-login (:login (get-in app state/user-path))
          user-orgs (get-in app state/user-organizations-path)
          elligible-transfer-orgs (-> (map :login user-orgs)
                                      (set)
                                      (conj user-login)
                                      (disj org-name)
                                      (sort))
          plan (get-in app state/org-plan-path)
          selected-transfer-org (get-in app state/selected-transfer-org-path)]
      [:div.row-fluid
       [:div.span8
        [:fieldset
         [:legend "Transfer plan to a different organization"]
         [:div.alert.alert-warning
          [:strong "Warning!"]
          [:p "If you're not an admin on the "
           (if (seq selected-transfer-org)
             (str selected-transfer-org " organization,")
             "organization you transfer to,")
           " then you won't be able to transfer the plan back or edit the plan."]
          [:p
           "The transferred plan will be extended to include the "
           org-name " organization, so your builds will continue to run. Only admins of the "
           (if (seq selected-transfer-org)
             (str selected-transfer-org " org")
             "organization you transfer to")
           " will be able to edit the plan."]]
         (if-not user-orgs
           [:div "Loading organization list..."]
           [:div.row-fluid
            [:div.span12
             [:div
              [:form
               [:div.controls
                (for [org elligible-transfer-orgs]
                  [:div.radio
                   [:label {:name org}
                    [:input {:value org
                             :checked (= org selected-transfer-org)
                             :on-change #(utils/edit-input owner state/selected-transfer-org-path %)
                             :type "radio"}]
                    org]])]
               [:div.form-actions.span6
                (forms/managed-button
                 [:button.btn.btn-danger.btn-large
                  {:data-success-text "Transferred",
                   :data-loading-text "Tranferring...",
                   :type "submit",
                   :class (when (empty? selected-transfer-org) "disabled")
                   :on-click #(do (raise! owner [:transfer-plan-clicked {:org-name org-name
                                                                         :to selected-transfer-org}])
                                  false)
                   :data-bind
                   "click: transferPlan, enable: transfer_org_name(), text: transfer_plan_button_text()"}
                  "Transfer plan" (when (seq selected-transfer-org) (str " to " selected-transfer-org))])]]]]])]]]))))

(defn organizations [app owner]
  (om/component
   (html
    [:div
     (om/build piggyback-organizations app)
     (om/build transfer-organizations app)])))

(defn- billing-card [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:checkout-loaded? (stripe/checkout-loaded?)
       :checkout-loaded-chan (chan)})
    om/IWillMount
    (will-mount [_]
      (let [ch (om/get-state owner [:checkout-loaded-chan])
            checkout-loaded? (om/get-state owner [:checkout-loaded?])]
        (when-not checkout-loaded?
          (go (<! ch) ;; wait for success message
              (utils/mlog "Stripe checkout loaded")
              (om/set-state! owner [:checkout-loaded?] true))
          (utils/mlog "Loading Stripe checkout")
          (stripe/load-checkout ch))))
    om/IWillUnmount
    (will-unmount [_]
      (close! (om/get-state owner [:checkout-loaded-chan])))
    om/IRenderState
    (render-state [_ {:keys [checkout-loaded?]}]
      (html
        (let [card (get-in app state/stripe-card-path)]
          (if-not (and card checkout-loaded?)
            [:div.row-fluid [:legend.span8 "Card on file"]
             [:div.row-fluid [:div.offset1.span6 [:div.loading-spinner common/spinner]]]]
            [:div
              [:div.row-fluid [:legend.span8 "Card on file"]]
              [:div.row-fluid
               [:div.offset1.span6
                [:table.table.table-condensed
                 {:data-bind "with: cardInfo"}
                 [:thead
                  [:th "Name"]
                  [:th "Card type"]
                  [:th "Card Number"]
                  [:th "Expiry"]]
                 (if (not (empty? card))
                   [:tbody
                    [:tr
                     [:td (:name card)]
                     [:td (:type card)]
                     [:td "xxxx-xxxx-xxxx-" (:last4 card)]
                     [:td (gstring/format "%02d" (:exp_month card)) \/ (:exp_year card)]]]
                   [:tbody
                    [:tr
                     [:td "N/A"]
                     [:td "N/A"]
                     [:td "N/A"]
                     [:td "N/A"]]])]]]
              [:div.row-fluid
               [:div.offset1.span7
                [:form.form-horizontal
                 [:div.control-group
                  [:div.control
                   (forms/managed-button
                     [:button#charge-button.btn.btn-primary.submit-button
                      {:data-success-text "Success",
                       :data-failed-text "Failed",
                       :data-loading-text "Updating",
                       :on-click #(do (raise! owner [:update-card-clicked])
                                      false)
                       :type "submit"}
                      "Change credit card"])]]]]]]))))))

;; Render a friendly human-readable version of a Stripe discount coupon.
;; Stripe has a convention for this that does not seem to be documented, so we
;; reverse engineer it here.
;; Examples from Stripe are:
;;     100% off for 1 month
;;     100% off for 6 months
;;  $100.00 off for 6 months
;;   $19.00 off for 12 months
;;      25% off forever
(defn format-discount
  [plan]
  (let [{ duration-in-months :duration_in_months
          percent-off        :percent_off
          amount-off         :amount_off
          duration           :duration
          id                 :id}  (get-in plan [:discount :coupon])
        discount-amount (if percent-off
                          (str percent-off "%")
                          (gstring/format "$%.2f" (/ amount-off 100)))
        discount-period (cond (= duration "forever") "forever"
                              (= duration-in-months 1) "for 1 month"
                              :else (gstring/format "for %d months" duration-in-months))]
    [:p "Your plan includes " discount-amount " off " discount-period
        " from coupon code " [:strong id]]))

;; Show a 'Discount' section showing any Stripe discounts that are being appied
;; the current plan.
;; Important: If there are no discounts, we don't want to show anything;
;; we do not want to tempt happy, paying customers to search online for discount
;; codes.
(defn- billing-discounts [app owner]
  (reify
    om/IRender
    (render [_]
      (html
        (let [plan (get-in app state/org-plan-path)]
          [:div.row-fluid
            (when (pm/has-active-discount? plan)
              [:fieldset
                [:legend.span8 "Discounts"]
                [:div.span8 (format-discount plan)]])])))))

(defn- billing-invoice-data [app owner]
  (reify
    om/IRender
    (render [_]
      (html
        (let [plan-data (get-in app state/org-plan-path)
              settings (state-utils/merge-inputs plan-data
                                                 (inputs/get-inputs-from-app-state owner)
                                                 [:billing_email :billing_name :extra_billing_data])]
          (if-not plan-data
            [:div.invoice-data.row-fluid
             [:legend.span8 "Invoice data"]
             [:div.row-fluid [:div.span8 [:div.loading-spinner common/spinner]]]]
            [:div.invoice-data.row-fluid
             [:fieldset
              [:legend.span8 "Invoice data"]
              [:form.form-horizontal.span8
               [:div.control-group
                [:label.control-label {:for "billing_email"} "Billing email"]
                [:div.controls
                 [:input.span10
                  {:value (str (:billing_email settings))
                   :name "billing_email",
                   :type "text"
                   :on-change #(utils/edit-input owner (conj state/inputs-path :billing_email) %)}]]]
               [:div.control-group
                [:label.control-label {:for "billing_name"} "Billing name"]
                [:div.controls
                 [:input.span10
                  {:value (str (:billing_name settings))
                   :name "billing_name",
                   :type "text"
                   :on-change #(utils/edit-input owner (conj state/inputs-path :billing_name) %)}]]]
               [:div.control-group
                [:label.control-label
                 {:for "extra_billing_data"}
                 "Extra data to include in your invoice"]
                [:div.controls
                 [:textarea.span10
                  {:value (str (:extra_billing_data settings))
                   :placeholder
                   "Extra information you would like us to include in your invoice, e.g. your company address or VAT ID.",
                   :rows 3
                   :name "extra_billing_data"
                   ;; FIXME These edits are painfully slow with the whitespace compiled Javascript
                   :on-change #(utils/edit-input owner (conj state/inputs-path :extra_billing_data) %)}]]]
               [:div.control-group
                [:div.controls
                 (forms/managed-button
                   [:button.btn.btn-primary
                    {:data-success-text "Saved invoice data",
                     :data-loading-text "Saving invoice data...",
                     :on-click #(do (raise! owner [:save-invoice-data-clicked])
                                    false)
                     :type "submit",}
                    "Save invoice data"])]]]]]))))))

(defn- invoice-total
  [invoice]
  (/ (:amount_due invoice) 100))

(defn- stripe-ts->date
  [ts]
  (datetime/year-month-day-date (* 1000 ts)))

(defn invoice-view
  "Render an invoice table row.
  Invoices fetched from the API look like:

  ;; Invoice API Format
  ;; ------------------
  ;; amount_due: 3206
  ;; currency: \"usd\"
  ;; date: 1403535350
  ;; id: \"in_2398vhs098AHYoi\"
  ;; paid: true
  ;; period_end: 1403535350
  ;; period_start: 1402665929"
  [invoice owner]
  (reify
    om/IRender
    (render [_]
      (html
        (let [invoice-id (:id invoice)]
          [:tr
            [:td (stripe-ts->date (:date invoice))]
            [:td (str (stripe-ts->date (:period_start invoice)))
                      " - "
                      (stripe-ts->date (:period_end invoice))]
            [:td.numeric (gstring/format "$%.2f" (invoice-total invoice))]
            [:td
              [:span
                (forms/managed-button
                  [:button.btn.btn-mini.btn-primary
                    {:data-failed-text "Failed",
                     :data-success-text "Sent",
                     :data-loading-text "Sending...",
                     :on-click #(do (raise! owner [:resend-invoice-clicked
                                                   {:invoice-id invoice-id}])
                                    false)}
                    "Resend"])]]])))))

(defn- ->balance-string [balance]
  (let [suffix (cond
                (< balance 0) " in credit."
                (> balance 0) " payment outstanding."
                :else "")
        amount (-> balance Math/abs (/ 100) .toLocaleString)]
    (str "$" amount suffix)))

(defn- billing-invoices [app owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (utils/popover "#invoice-popover-hack"
                     {:animation false
                      :trigger "hover"
                      :html true})
      (utils/tooltip "#resend-invoice-tooltip-hack"
                     {:animation false}))
    om/IRender
    (render [_]
      (html
        (let [account-balance (get-in app state/org-plan-balance-path)
              invoices (get-in app state/org-invoices-path)]
          (if-not (and account-balance invoices)
            [:div.row-fluid
             [:div.span8
               [:legend "Invoices"]
              [:div.loading-spinner common/spinner]]]
            [:div.row-fluid
             [:div.span8
               [:legend "Invoices"]
               [:dl.dl-horizontal
                [:dt
                 "Account balance"
                 [:i.fa.fa-question-circle#invoice-popover-hack
                  {:title "Account balance"
                   :data-content (str "<p>This is the credit you have with Circle. If your credit is positive, then we will use it before charging your credit card.</p>"
                                      "<p>Contact us if you'd like us to send you a refund for the balance.</p>"
                                      "<p>This amount may take a few hours to refresh.</p>")}]]
                [:dd
                 [:span (->balance-string account-balance)]]]
               [:table.table.table-bordered.table-striped
                [:thead
                 [:tr
                  [:th "Invoice date"]
                  [:th "Time period covered"]
                  [:th "Total"]
                  [:th
                   [:i.fa.fa-question-circle#resend-invoice-tooltip-hack
                    {:title "Resend an invoice to the billing email above."}]
                   "Actions"]]]
                [:tbody
                 (om/build-all invoice-view invoices)]]]]))))))

(defn billing [app owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div.plans
          (om/build billing-card app)
          (om/build billing-invoice-data app)
          (om/build billing-discounts app)
          (om/build billing-invoices app)]))))

(defn cancel [app owner]
  (reify
    om/IRender
    (render [_]
      (let [org-name (get-in app state/org-name-path)
            plan (get-in app state/org-plan-path)]
        (html
          [:div.org-cancel
           [:div.row-fluid [:fieldset [:legend "Is this goodbye? Are you sure you don't want to reconsider?"]]]
           [:div.top-value
            [:p.value-prop "If you cancel your plan, you will no longer have access to speed enabled by parallelism and concurrency."]
            [:p.value-prop "You will also lose access to engineer support, insights, and more premium features."]]
           [:div.bottom-value
            [:p.value-prop "Your cancelation will be effective immediately"]]
          [:div.row-fluid
           [:h1
            {:data-bind "attr: {alt: cancelFormErrorText}"}
            "Please tell us why you're canceling. This helps us make Circle better!"]
           [:form
            (for [reason [{:value "project-ended", :text "Project Ended"},
                          {:value "slow-performance", :text "Slow Performance"},
                          {:value "unreliable-performance", :text "Unreliable Performance"},
                          {:value "too-expensive", :text "Too Expensive"},
                          {:value "didnt-work", :text "Couldn't Make it Work"},
                          {:value "missing-feature", :text "Missing Feature"},
                          {:value "poor-support", :text "Poor Support"},
                          {:value "other", :text "Other"}]]
              [:label.cancel-reason
               [:input
                {:checked (get-in app (state/selected-cancel-reason-path (:value reason)))
                 :on-change #(utils/toggle-input owner (state/selected-cancel-reason-path (:value reason)) %)
                 :type "checkbox"}]
               (:text reason)])
            [:textarea
             {:required true
              :value (get-in app state/cancel-notes-path)
              :on-change #(utils/edit-input owner state/cancel-notes-path %)}]
            [:label
             {:placeholder "Thanks for the feedback!",
              :alt (if (get app (state/selected-cancel-reason-path "other"))
                     "Would you mind elaborating more?"
                     "Have any other thoughts?")}]
            (let [reasons (->> (get-in app state/selected-cancel-reasons-path)
                                                    (filter second)
                                                    keys
                                                    set)
                  notes (get-in app state/cancel-notes-path)
                  errors (cond (empty? reasons) "Please select at least one reason."
                               (and (contains? reasons "other") (string/blank? notes)) "Please specify above."
                               :else nil)]
              ;; This is a bit of a hack -- it could be much nicer if managed button exposed more of its interface
              ;; or accepted hooks
              (if errors
                (list
                 (when (om/get-state owner [:show-errors?])
                   [:div.hint {:class "show"} [:i.fa.fa-exclamation-circle] " " errors])
                 [:button {:on-click #(do (om/set-state! owner [:show-errors?] true) false)}
                  "Cancel Plan"])
                (forms/managed-button
                 [:button {:data-spinner "true"
                           :on-click #(do (raise! owner [:cancel-plan-clicked {:org-name org-name
                                                                               :cancel-reasons reasons
                                                                               :cancel-notes notes}])
                                          false)}
                  "Cancel Plan"])))]]])))))

(defn progress-bar [{:keys [max value class]} owner]
  (reify
    om/IRender
    (render [_]
      (html [:progress {:class class :value value :max max} (str value "%")]))))

(defn usage-bar [{:keys [usage max month]} owner]
  (reify
    om/IRender
    (render [_]
      (let [percent (.round js/Math (* 100 (/ usage max)))]
        (html
         [:div.usage-group
          [:div.month-label month]
          (om/build progress-bar {:class "monthly-usage-bar" :max max :value usage})
          [:div.usage-label
           (when (>= percent 100) {:class "over-usage"})
           [:div.percent-label (str percent "%")]
           [:div.amounts-label
            (str (.toLocaleString usage) "/" (.toLocaleString max) " minutes")]]])))))

(defn usage-key->date [usage-key]
  (time-format/parse (time-format/formatter "yyyy_MM") (name usage-key)))

(defn osx-usage-table [{:keys [plan]} owner]
  (reify
    om/IRender
    (render [_]
      (let [org-name (:org_name plan)
            osx-max-minutes (some-> plan :osx :template :max_minutes)
            osx-usage (-> plan :usage :os:osx)]
        (html
         [:div
          [:fieldset [:legend (str org-name "'s iOS usage")]]
          (if (and (not-empty osx-usage) osx-max-minutes)
            (let [osx-usage (->> osx-usage
                                 (sort)
                                 (reverse)
                                 (take 12)
                                 (map (fn [[month amount]]
                                        {:usage (.round js/Math (/ amount 1000 60))
                                         :max osx-max-minutes
                                         :month ((comp datetime/date->month-name usage-key->date) month)})))]
              [:div.monthly-usage
               (om/build-all usage-bar osx-usage)])
            [:div.explanation
             [:p "Looks like you haven't run any builds yet."]])])))))

(defn osx-overview [{:keys [plan osx-enabled?]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div
         [:h2 "iOS"]
         (if-not osx-enabled?
           [:p "You are not currently in the iOS limited-release. If you would like access to iOS builds, please send an email to sayhi@circleci.com."]
           [:div
            [:p "You are in the iOS limited-release, you may also choose an iOS plan "
             [:a {:href "#containers"} "here"] "."]
            (when (pm/osx? plan)
              (let [plan-name (some-> plan :osx :template :name)
                    plan-start (some-> plan :osx_plan_started_on)
                    trial-end (some-> plan :osx_trial_end_date)]
                [:p
                 (if (pm/osx-trial-active? plan)
                   (gstring/format "You're currently on the iOS trial for %d more days. "
                                   (datetime/format-duration (time/in-millis (time/interval (js/Date. plan-start) (js/Date. trial-end))) :days))
                   (gstring/format "Your current iOS plan is %s ($%d/month). " plan-name (pm/osx-cost plan)))
                 [:span "We will support general release in the near future!"]]))])]))))

(defn overview [app owner]
  (om/component
   (html
    (let [org-name (get-in app state/org-name-path)
          osx-enabled? (get-in app state/org-osx-enabled-path)
          plan (get-in app state/org-plan-path)
          plan-total (pm/stripe-cost plan)
          container-cost (pm/per-container-cost plan)
          price (-> plan :paid :template :price)
          containers (pm/usable-containers plan)
          piggiebacked? (pm/piggieback? plan org-name)]
      [:div
       [:fieldset [:legend (str org-name "'s plan")]]
       [:div.explanation
        (when piggiebacked?
          [:p "This organization's projects will build under "
           [:a {:href (routes/v1-org-settings {:org (:org_name plan)})}
            (:org_name plan) "'s plan."]])
        [:h2 "Linux"]
        (cond (> containers 1)
              [:p (str "All Linux builds will be distributed across " containers " containers.")]
              (= containers 1)
              [:div
               [:p (str org-name " is currently on the Hobbyist plan. Builds will run in a single, free container.")]
               [:p "By " [:a {:href (routes/v1-org-settings-subpage {:org (:org_name plan)
                                                               :subpage "containers"})}
                    "upgrading"]
                (str " " org-name "'s plan, " org-name " will gain access to concurrent builds, parallelism, engineering support, insights, build timings, and other cool stuff.")]]
              :else nil)
        (when (> (pm/trial-containers plan) 0)
          [:p
           (str (pm/trial-containers plan) " of these are provided by a trial. They'll be around for "
                (pluralize (pm/days-left-in-trial plan) "more day")
                ".")])
        (when (pm/paid? plan)
          [:p
           (str (pm/paid-containers plan) " of these are paid")
           (if piggiebacked? ". "
               (list ", at $" (pm/linux-cost plan) "/month. "))
           (if (pm/grandfathered? plan)
             (list "We've changed our pricing model since this plan began, so its current price "
                   "is grandfathered in. "
                   "It would be $" (pm/cost plan (pm/usable-containers plan)) " at current prices. "
                   "We'll switch it to the new model if you upgrade or downgrade. ")
             (list
              "You can "
              ;; make sure to link to the add-containers page of the plan's org,
              ;; in case of piggiebacking.
              [:a {:href (routes/v1-org-settings-subpage {:org (:org_name plan)
                                                          :subpage "containers"})}
               "add more"]
              (when-not piggiebacked?
                (list " at $" container-cost " per container"))
              " for more parallelism and shorter queue times."))])
        (when (and (pm/freemium? plan) (> containers 1))
          [:p (str (pm/freemium-containers plan) " container is free.")])
        (when-not (config/enterprise?)
          [:div
           [:p "Additionally, projects that are public on GitHub will build with " pm/oss-containers " extra containers -- our gift to free and open source software."]
           (om/build osx-overview {:plan plan
                                   :osx-enabled? osx-enabled?})])
        (when (and (feature/enabled? :ios-build-usage)
                   (pm/osx? plan))
          (om/build osx-usage-table {:plan plan}))]]))))

(def main-component
  {:overview overview
   :users users
   :projects projects
   :containers containers
   :organizations organizations
   :billing billing
   :cancel cancel})

(defn org-settings [app owner]
  (reify
    om/IRender
    (render [_]
      (let [org-data (get-in app state/org-data-path)
            subpage (or (get app :org-settings-subpage) :overview)
            plan (get-in app state/org-plan-path)]
        (html [:div.org-page
               (if-not (:loaded org-data)
                 [:div.loading-spinner common/spinner]
                 [:div
                  (when (pm/suspended? plan)
                    (om/build project-common/suspended-notice plan))
                  (om/build common/flashes (get-in app state/error-message-path))
                  [:div#subpage
                   [:div
                    (if (:authorized? org-data)
                      (om/build (get main-component subpage projects) app)
                      [:div (om/build non-admin-plan
                                      {:login (get-in app [:current-user :login])
                                       :org-name (:org-settings-org-name app)
                                       :subpage subpage})])]]])])))))
