(ns frontend.components.org-settings
  (:require [cljs.core.async :as async :refer [<! chan close!]]
            clojure.set
            [clojure.string :as string]
            [frontend.analytics.track :as analytics-track]
            [frontend.async :refer [navigate! raise!]]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.inputs :as inputs]
            [frontend.components.pieces.table :as table]
            [frontend.components.pieces.tabs :as tabs]
            [frontend.components.project.common :as project-common]
            [frontend.components.shared :as shared]
            [frontend.config :as config]
            [frontend.datetime :as datetime]
            [frontend.models.organization :as org-model]
            [frontend.models.plan :as pm]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.stripe :as stripe]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.state :as state-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring]
            [inflections.core :as infl :refer [pluralize]]
            [om.core :as om :include-macros true])
  (:require-macros [cljs.core.async.macros :as am :refer [go]]
                   [frontend.utils :refer [html]]))

(defn non-admin-plan [{:keys [org-name login vcs_type]} owner]
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
                [:a {:href (routes/v1-org-settings-path {:org login
                                                         :vcs_type vcs_type
                                                         :_fragment "containers"})}
                 "\"personal organization\" page"]]
               [:li
                "Add " org-name
                " to the list of organizations you pay for or transfer the plan to "
                org-name " from the "
                [:a {:href (routes/v1-org-settings-path {:org login
                                                         :vcs_type vcs_type
                                                         :_fragment "organizations"})}
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
          [:article
           [:legend
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
                     [:a {:href (routes/v1-project-dashboard-path {:org (vcs-url/org-name vcs-url)
                                                                   :repo (vcs-url/repo-name vcs-url)
                                                                   :vcs_type (vcs-url/vcs-type vcs-url)})}
                      (vcs-url/project-name vcs-url)]])]]])]]]])))))

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
           [:article
            [:legend "Followed projects"]
            (if-not (seq followed-projects)
              [:h3 "No followed projects found."]

              [:div.span8
               (for [project followed-projects
                     :let [vcs-url (:vcs_url project)]]
                 [:div.row-fluid
                  [:div.span12.well

                    [:div.project-header
                     [:span.project-name
                      [:a {:href (routes/v1-project-dashboard-path {:org (vcs-url/org-name vcs-url)
                                                                    :repo (vcs-url/repo-name vcs-url)
                                                                    :vcs_type (vcs-url/vcs-type vcs-url)})}
                       (vcs-url/project-name vcs-url)]
                      " "]
                     [:div.github-icon
                      [:a {:href vcs-url}
                       [:i.octicon.octicon-mark-github]]]
                     [:div.settings-icon
                      [:a.edit-icon {:href (routes/v1-project-settings-path {:org (vcs-url/org-name vcs-url)
                                                                             :repo (vcs-url/repo-name vcs-url)
                                                                             :vcs_type (vcs-url/vcs-type vcs-url)})}
                       [:i.material-icons "settings"]]]]
                   (om/build followers-container (:followers project))]])])]
           [:div.row-fluid
            [:h1 "Untested projects"]
            (if-not (seq unfollowed-projects)
              [:h3 "No untested projects found."]

              [:div.span8
               (for [project unfollowed-projects
                     :let [vcs-url (:vcs_url project)]]
                 [:div.row-fluid
                  [:div.span12.well

                    [:div.project-header
                     [:span.project-name
                      [:a {:href (routes/v1-project-dashboard-path {:org (vcs-url/org-name vcs-url)
                                                                    :repo (vcs-url/repo-name vcs-url)
                                                                    :vcs_type (vcs-url/vcs-type vcs-url)})}
                       (vcs-url/project-name vcs-url)]
                      " "]
                     [:div.github-icon
                      [:a {:href vcs-url}
                       [:i.octicon.octicon-mark-github]]]
                     [:div.settings-icon
                      [:a.edit-icon {:href (routes/v1-project-settings-path {:org (vcs-url/org-name vcs-url)
                                                                             :repo (vcs-url/repo-name vcs-url)
                                                                             :vcs_type (vcs-url/vcs-type vcs-url)})}
                       [:i.material-icons "settings"]]]]
                   (om/build followers-container (:followers project))]])])]]])))))

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
  (let [containers (pm/paid-linux-containers plan)]
    (str
      (when (pos? containers)
        (str containers " containers"))
      (when (and (pos? containers) (pm/osx? plan))
        " and ")
      (when (pm/osx? plan)
        (-> plan :osx :template :name)))))

(defn plans-piggieback-plan-notification [{{parent-name :name
                                            parent-vcs-type :vcs_type} :org
                                           :as plan}
                                          current-org-name]
  [:div.row-fluid
   [:div.offset1.span10
    [:div.alert.alert-success
     [:p
      "This organization is covered under " [:em parent-name] "'s plan which has " (piggieback-plan-wording plan)]
     [:p
      "If you're an admin in the " parent-name
      " organization, then you can change plan settings from the "
      [:a {:href (routes/v1-org-settings-path {:org parent-name
                                               :vcs_type parent-vcs-type})}
       parent-name " plan page"] "."]
     [:p
      "You can create a separate plan for " [:em current-org-name] " when you're no longer covered by " [:em parent-name] "."]]]])

(defn plural-multiples [num word]
  (if (> num 1)
    (pluralize num word)
    word))

(defn pluralize-no-val [num word]
  (if (= num 1) (infl/singular word) (infl/plural word)))

(def osx-faq-items
  [{:question "Why would I choose an OS X plan (as opposed to a Linux Plan)?"
    :answer [[:div "If you develop for Apple-related software (e.g., you are an iOS developer), you will likely need an OS X plan to ensure your tests run on our secure, private cloud of OS X machines."]
             [:div "OS X plans start with a two-week trial with access to our Growth Plan (7x concurrent builds). At the end of your two-week trial, you may choose the plan that fits your needs best."]
             [:div "Linux plans allow customers to build on multiple isolated Linux systems. All customers get 1 free linux container and then Linux plans offer access to additional containers available at $50/container."]]}

   {:question "What plan do I need?"
    :answer [[:div "We’ve provided recommendations based on the ranges of builds but every team is different regarding their frequency and length of builds - as well as their need for engineer support."]
             [:div "To solve for this, everyone starts with a free 2-week trial at the Growth Plan which will help you determine all of those factors. We are happy to help you work through which plan is right for your team."]]}

   {:question "What is concurrency?"
    :answer [[:div "Concurrency refers to running multiple jobs at the same time (e.g., with 2x concurrency, two builds triggered at the same time will both kick off, but on 1x concurrency one build will queue, waiting for resources)."]
             [:div "Concurrency avoids slow-downs in work as your builds may otherwise queue behind the builds of someone else on your team."]]}


   {:question "*What if I go over the minutes allotted for a given plan?"
    :answer [[:div  "Minutes and overages ensure we can stabilize capacity while offering as much power as possible which should hopefully lead to the greatest possible utility all around."]
             [:p "Overages are as follows:"]
             [:ul.overage-list
              [:li "Seed & Startup: .08/minute"]
              [:li "Growth: .05/minute"]
              [:li "Mobile Focused: .035/minute"]]
             [:div "Users will be alerted in-app as they approach the limit and upon passing their respective limit."]
             [:div
              "Feel free to reach out to "
              [:a {:href "mailto:billing@circleci.com"} "billing@circleci.com"]
              " with any additional questions"]]}

   {:question "Can I change my plan at a later time? Can I cancel anytime?"
    :answer [[:div "Yes and yes!"]
             [:div "You can visit this page to upgrade, downgrade, or cancel your plan at any time."]]}

   {:question "What if I want something custom?"
    :answer [[:div "Feel free to contact us "
              [:a {:href "mailto:billing@circleci.com"} "billing@circleci.com"]]]}

   {:question "What if I am building open-source?"
    :answer [[:div "We also offer the Seed plan for OS X open-source projects. Contact us at "
              [:a {:href "mailto:billing@circleci.com"} "billing@circleci.com"]
              " for access. If you are building a bigger open-source project and need more resources, let us know how we can help you!"]]}])

(def linux-faq-items
  [{:question "How do I get started?"
    :answer [[:div "Linux plans start with the ability to run one build simultaneously without parallelism at no charge. Open source projects get 3 additional free containers, so you can use the power of parallelism and/or run more than one build concurrently. Purchasing a Linux plan enables access to additional containers at $50/container/month."]
             [:div "During the signup process, you can decide which plan(s) you need - you can build for free regardless!"]]}

   {:question "How do containers work?"
    :answer [[:div "Every time you push to your VCS system, we checkout your code and run your build inside of a fresh, on-demand, and isolated Linux container pre-loaded with most popular languages, tools, and framework. CircleCI, in many cases, will detect and automatically download and cache your dependencies, and you can fully script any steps or integrations."]]}

   {:question "What is concurrency? What is parallelism?"
    :answer [[:div "Concurrency refers to utilizing multiple containers to run multiple builds at the same time. Otherwise, if you don't have enough free containers available, your builds queue up until other builds finish."]
             [:div "Parallelism splits a given build’s tests across multiple containers, allowing you to dramatically speed up your test suite. This enables your developers to finish even the most test-intensive builds in a fraction of the time."]]}

   {:question "How many containers do I need?"
    :answer [[:div "Most of our customers tend to use about 2-3 containers per full-time developer. Every team is different, however, and we're happy to set you up with a trial to help you figure out how many works best for you. As your team grows and/or as the speed of your build grows you can scale to any number of containers at any level of parallelism and concurrency is right for your team."]]}

   {:question "Why should I trust CircleCI with my code?"
    :answer [[:div "Security is one of our top priorities. Read our security policy to learn more about why many other customers rely on CircleCI to keep their code safe."]]}

   {:question "Can I change my plan at a later time? Can I cancel anytime?"
    :answer [[:div "Yes and yes! We offer in-app tools to fully control your plan and have account managers and an international support team standing by when you need help."]]}

   {:question "What if I want something custom?"
    :answer [[:div "Feel free to contact us "
              [:a {:href "mailto:billing@circleci.com"} "billing@circleci.com"]]]}

   {:question "What if I am building open-source?"
    :answer [[:div "We offer a total of four free linux containers ($2400 annual value) for open-source projects. Simply keeping your project public will enable this for you!"]]}])

(defn faq-answer-line [answer-line owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div.answer-line answer-line]))))

(defn faq-item [{:keys [question answer]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div.faq-item
         [:h1.question question]
         [:div.answer
          (om/build-all faq-answer-line answer)]]))))

(defn faq [items owner]
  (reify
    om/IRender
    (render [_]
      (let [[first-half second-half] (split-at (quot (count items) 2) items)]
       (html
         [:fieldset.faq {:data-component `faq}
          [:legend "FAQs"]
          [:div.column
           (om/build-all faq-item first-half)]
          [:div.column
           (om/build-all faq-item second-half)]])))))

(defn plan-payment-button [{:keys [text loading-text disabled? on-click-fn]} owner]
  (reify
    om/IRender
    (render [_]
      (button/managed-button
       {:success-text "Success!"
        :loading-text loading-text
        :failed-text "Failed"
        :on-click on-click-fn
        :kind :primary
        :disabled? disabled?}
       text))))

(defn osx-plan [{:keys [title price container-count daily-build-count max-minutes support-level team-size
                        plan-id plan trial-starts-here? org-name vcs-type]} owner]
  (reify
    om/IRender
    (render [_]
      (let [plan-data (get-in pm/osx-plans [plan-id])
            currently-selected? (= (name plan-id) (pm/osx-plan-id plan))
            on-trial? (and trial-starts-here? (pm/osx-trial-plan? plan))
            trial-expired? (and on-trial? (not (pm/osx-trial-active? plan)))
            trial-starts-here? (and trial-starts-here?
                                    (pm/trial-eligible? plan :osx)
                                    (not (pm/osx? plan)))]
        (html
          [:div {:data-component `osx-plan}
           [:div {:class (cond currently-selected? "plan-notice selected-notice"
                               trial-expired? "plan-notice trial-expired-notice"
                               on-trial? "plan-notice selected-notice"
                               trial-starts-here?  "plan-notice trial-notice"
                               :else "no-notice")}
            [:div.plan
             [:div.header
              [:div.title title]
              [:div.price "$" [:span.bold price] "/mo"]]
             [:div.content
              [:div.containers [:span.bold container-count] " OS X concurrency"]
              [:div.daily-builds
               [:div "Recommended for teams building "]
               [:div.bold daily-build-count " builds/day"]]
              [:div.max-minutes [:span.bold max-minutes] " max minutes/month" [:sup.bold "*"]]
              [:div.support support-level]
              [:div.team-size "Recommended for " [:span.bold team-size]]
              [:div " team members"]]
             [:div.action
              (if (pm/stripe-customer? plan)
                (om/build plan-payment-button {:text "Update"
                                               :loading-text "Updating..."
                                               :disabled? (= (name plan-id) (pm/osx-plan-id plan))
                                               :on-click-fn #(do
                                                               (raise! owner [:update-osx-plan-clicked {:plan-type {:template (name plan-id)}}])
                                                               (analytics-track/update-plan-clicked {:owner owner
                                                                                                     :new-plan plan-id
                                                                                                     :previous-plan (pm/osx-plan-id plan)
                                                                                                     :plan-type pm/osx-plan-type
                                                                                                     :upgrade? (> (:price plan-data) (pm/osx-cost plan))}))})
                (om/build plan-payment-button {:text "Pay Now"
                                               :loading-text "Paying..."
                                               :on-click-fn #(do
                                                               (raise! owner [:new-osx-plan-clicked {:plan-type {:template (name plan-id)}
                                                                                                     :price (:price plan-data)
                                                                                                     :description (gstring/format "OS X %s - $%d/month "
                                                                                                                                  (clojure.string/capitalize (name plan-id))
                                                                                                                                  (:price plan-data))}])
                                                               ((om/get-shared owner :track-event) {:event-type :new-plan-clicked
                                                                                                    :properties {:plan-type pm/osx-plan-type
                                                                                                                 :plan plan-id}}))}))

              (if (not (pm/osx? plan))
                (when trial-starts-here?
                  [:div.start-trial "OR" [:br]
                   (forms/managed-button
                     [:a
                      {:data-success-text "Success!"
                       :data-loading-text "Starting..."
                       :data-failed-text "Failed"
                       :on-click #(raise! owner [:activate-plan-trial {:plan-type :osx
                                                                       :template "osx-trial"
                                                                       :org (:org plan)}])}
                      "start a 2-week free trial"])])
                (when currently-selected?
                  [:div.cancel-plan "OR" [:br]
                   (forms/managed-button
                     [:a
                      {:data-success-text "Success!"
                       :data-loading-text "Cancelling..."
                       :data-failed-text "Failed"
                       :on-click #(raise! owner [:cancel-plan-clicked {:org-name org-name
                                                                       :vcs_type vcs-type
                                                                       :plan-type :osx}])}
                      "cancel your current OSX plan"])]))]]

            (cond
              trial-starts-here?
              [:div.bottom "FREE TRIAL STARTS HERE"]

              trial-expired?
              [:div.bottom "Trial has Ended - Choose a Plan"]

              on-trial?
              [:div.bottom
               (str "Trial plan ("(pm/osx-trial-days-left plan)" left)")]

              currently-selected?
              [:div.bottom "Your Current Plan"])]])))))

(defn osx-plans-list [{:keys [plan org-name vcs-type]} owner]
  (reify
    om/IRender
    (render [_]
      (let [osx-plans (->> pm/osx-plans
                           (vals)
                           (map (partial merge {:plan plan})))]
        (html
          [:div.osx-plans {:data-component `osx-plans-list}
           [:fieldset
            [:legend (str "OS X Plans")]
            [:p "Your selection below only applies to OS X service and will not affect Linux Containers."]
            (when (and (pm/osx-trial-plan? plan) (not (pm/osx-trial-active? plan)))
              [:p "The OS X trial you've selected has expired, please choose a plan below."])
            (when (and (pm/osx-trial-plan? plan) (pm/osx-trial-active? plan))
              [:p (gstring/format "You have %s left on the OS X trial." (pm/osx-trial-days-left plan))])]
           [:div.plan-selection
            (om/build-all osx-plan (->> osx-plans
                                        (map #(assoc % :org-name org-name :vcs-type vcs-type))))]])))))

(defn linux-plan [{:keys [app checkout-loaded?]} owner]
  (reify
    om/IRender
    (render [_]
      (let [org-name (get-in app state/org-name-path)
            vcs-type (get-in app state/org-vcs_type-path)
            plan (get-in app state/org-plan-path)
            selected-containers (or (get-in app state/selected-containers-path)
                                    (if (config/enterprise?)
                                      (pm/enterprise-containers plan)
                                      (pm/paid-linux-containers plan)))
            login (get-in app state/user-login-path)
            view (get-in app state/current-view-path)
            selected-paid-containers (max 0 selected-containers)
            osx-total (or (some-> plan :osx :template :price) 0)
            old-total (- (pm/stripe-cost plan) osx-total)
            new-total (pm/linux-cost plan (+ selected-containers (pm/freemium-containers plan)))
            linux-container-cost (pm/linux-per-container-cost plan)
            piggiebacked? (pm/piggieback? plan org-name vcs-type)
            button-clickable? (not= (if piggiebacked? 0 (pm/paid-linux-containers plan))
                                    selected-paid-containers)
            containers-str (pluralize-no-val selected-containers "container")]
       (html
         [:div#edit-plan {:class "pricing.page" :data-component `linux-plan}
          [:div.main-content
           [:div
            [:legend "Linux Plan: "
             [:div.container-input
              [:input.form-control {:style {:margin "0 4px" :height "calc(2em + 2px)"}
                                    :type "text" :value selected-containers
                                    :on-change #(utils/edit-input owner state/selected-containers-path %
                                                                  :value (int (.. % -target -value)))}]]
             [:span.new-plan-total (if (config/enterprise?)
                                    containers-str
                                    (str "paid " containers-str
                                         (str (when-not (zero? new-total) (str " for $" new-total "/month")))
                                         " + 1 free container"))]]

            [:form
             (when-not (config/enterprise?)
               [:div.container-picker
                [:h1 "More containers means faster builds and lower queue times."]
                [:p (str "Our pricing is flexible and scales with you. Add as many containers as you want for $" linux-container-cost "/month each.")]])
             [:fieldset
              (if (and (not piggiebacked?)
                       (or (config/enterprise?)
                           (pm/stripe-customer? plan)))
                (let [enterprise-text "Save changes"]
                  (if (and (zero? new-total)
                           (not (config/enterprise?))
                           (not (zero? (pm/paid-linux-containers plan))))
                    (button/link
                     {:href "#cancel"
                      :disabled? (not button-clickable?)
                      :kind :danger
                      :on-click #((om/get-shared owner :track-event) {:event-type :cancel-plan-clicked
                                                                      :properties {:repo nil}})}
                     "Cancel Plan")
                    (button/managed-button
                     {:success-text "Saved"
                      :loading-text "Saving..."
                      :disabled? (not button-clickable?)
                      :on-click (when button-clickable?
                                  #(do
                                    (raise! owner [:update-containers-clicked
                                                   {:containers selected-paid-containers}])
                                    (analytics-track/update-plan-clicked {:owner owner
                                                                          :new-plan selected-paid-containers
                                                                          :previous-plan (pm/paid-linux-containers plan)
                                                                          :plan-type pm/linux-plan-type
                                                                          :upgrade? (> selected-paid-containers (pm/paid-linux-containers plan))})))
                      :kind :primary}
                     (if (config/enterprise?)
                       enterprise-text
                       "Update Plan"))))
                (if-not checkout-loaded?
                  (spinner)
                  (button/managed-button
                   {:success-text "Paid!"
                    :loading-text "Paying..."
                    :failed-text "Failed!"
                    :disabled? (not button-clickable?)
                    :on-click (when button-clickable?
                                #(raise! owner [:new-plan-clicked
                                                {:containers selected-paid-containers
                                                 :linux {:template (:id pm/default-template-properties)}
                                                 :price new-total
                                                 :description (str "$" new-total "/month, includes "
                                                                   (pluralize selected-containers "container"))}]))
                    :kind :primary}
                   "Pay Now")))

              (when-not (config/enterprise?)
               ;; TODO: Clean up conditional here - super nested and many interactions
                (if (or (pm/linux? plan) (and (pm/freemium? plan) (not (pm/in-trial? plan))))
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
                     " ago. Pay now to enable builds of private projects."])))]]]]])))))

(defn pricing-tabs [{:keys [app plan checkout-loaded? selected-tab-name]} owner]
  (reify
    om/IRender
    (render [_]
      (let [{{org-name :name
              vcs-type :vcs_type} :org} plan]
        (card/tabbed
         {:tab-row
          (om/build tabs/tab-row {:tabs [{:name :linux
                                          :icon (html [:i.fa.fa-linux.fa-lg])
                                          :label "Build on Linux"}
                                         {:name :osx
                                          :icon (html [:i.fa.fa-apple.fa-lg])
                                          :label "Build on OS X"}]
                                  :selected-tab-name selected-tab-name
                                  :on-tab-click #(navigate! owner (routes/v1-org-settings-path {:org org-name
                                                                                                :vcs_type vcs-type
                                                                                                :_fragment (str (name %) "-pricing")}))})}
         (case selected-tab-name
           :linux (list
                   (om/build linux-plan {:app app :checkout-loaded? checkout-loaded?})
                   (om/build faq linux-faq-items))

           :osx (list
                 (om/build osx-plans-list {:plan plan
                                           :org-name (get-in app state/org-name-path)
                                           :vcs-type (get-in app state/org-vcs_type-path)})
                 (om/build faq osx-faq-items))))))))

(defn pricing-starting-tab [subpage]
  (get {:osx-pricing :osx
        :linux-pricing :linux} subpage :linux))

(defn cloud-pricing [app owner]
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
            org-name (get-in app state/org-name-path)
            org-vcs-type (get-in app state/org-vcs_type-path)]
        (html
          (if-not plan
            (cond ;; TODO: fix; add plan
              (nil? plan)
              (spinner)
              (not (seq plan))
              [:h3 (str "No plan exists for" org-name "yet. Follow a project to trigger plan creation.")]
              :else
                [:h3 "Something is wrong! Please submit a bug report."])

            (if (pm/piggieback? plan org-name org-vcs-type)
              (plans-piggieback-plan-notification plan org-name)
              [:div
               (om/build pricing-tabs {:app app :plan plan :checkout-loaded? checkout-loaded?
                                       :selected-tab-name (pricing-starting-tab (get-in app state/org-settings-subpage-path))})])))))))

(defn piggieback-org-list [piggieback-orgs selected-orgs [{vcs-type :vcs_type} :as vcs-users-and-orgs] owner]
  (let [;; split user orgs from real ones so we can later cons the
        ;; user org onto the list of orgs
        {[{vcs-user-name :login}] nil
         vcs-orgs true}
        (group-by :org vcs-users-and-orgs)
        vcs-org-names (->> vcs-orgs
                           (map :login)
                           set)
        vcs-rider-names (into #{}
                              (comp
                               (filter #(= vcs-type (:vcs_type %)))
                               (map :name))
                              piggieback-orgs)]
    ;; width has been set to 20% here
    [:div.controls.col-md-3
     [:h4 (str (utils/prettify-vcs_type vcs-type) " Organizations")]
     ;; orgs that this user can add to piggieback orgs and existing piggieback orgs
     (for [org-name (cond->> (disj (clojure.set/union vcs-org-names
                                                      vcs-rider-names)
                                   vcs-user-name)
                      true (sort-by string/lower-case)
                      vcs-user-name (cons vcs-user-name))]
       [:div.checkbox
        [:label
         [:input
          (let [checked? (contains? selected-orgs {:name org-name
                                                   :vcs_type vcs-type})]
            {:value org-name
             :checked checked?
             :on-change (fn [event]
                          (raise! owner [:selected-piggieback-orgs-updated {:org {:name org-name
                                                                                  :vcs_type vcs-type}
                                                                            :selected? (not checked?)}]))
             :type "checkbox"})]
         org-name]])]))

(defn piggieback-organizations [{{org-name :name
                                  org-vcs_type :vcs_type
                                  {piggieback-orgs :piggieback_org_maps} :plan
                                  :keys [selected-piggieback-orgs]} :current-org
                                 :keys [user-orgs]} owner]
  (reify
    om/IRender
    (render [_]
      (html
       (let [{gh-users-and-orgs "github"
              bb-users-and-orgs "bitbucket"}
             (->> user-orgs
                  (remove #(and (= (:login %) org-name)
                                (= (:vcs_type %) org-vcs_type)))
                  (group-by :vcs_type))]
         [:div
          [:fieldset
           [:legend "Extra organizations"]
           [:p
            "Your plan covers all projects (including forked repos) in the "
            [:strong org-name]
            " organization by default."]
           [:p "You can let any GitHub organization you belong to, including personal accounts, piggieback on your plan. Projects in your piggieback organizations will be able to run builds on your plan."]
           [:p
            [:span.label.label-info "Note:"]
            " Members of the piggieback organizations will be able to see that you're paying for them, the name of your plan, and the number of containers you've paid for. They won't be able to edit the plan unless they are also admins on the " org-name " org."]
           (if-not (or gh-users-and-orgs bb-users-and-orgs)
             [:div "Loading organization list..."]
             [:form
              [:div.container-fluid
               [:div.row
                (when (seq gh-users-and-orgs)
                  (piggieback-org-list piggieback-orgs selected-piggieback-orgs gh-users-and-orgs owner))
                (when (seq bb-users-and-orgs)
                  (piggieback-org-list piggieback-orgs selected-piggieback-orgs bb-users-and-orgs owner))]
               [:div.row
                [:div.form-actions.span7
                 (button/managed-button
                  {:success-text "Saved"
                   :loading-text "Saving..."
                   :failed-text "Failed"
                   :on-click #(raise! owner [:save-piggieback-orgs-clicked {:org-name org-name
                                                                            :vcs-type org-vcs_type
                                                                            :selected-piggieback-orgs selected-piggieback-orgs}])
                   :kind :primary}
                  "Save")]]]])]])))))

(defn transfer-organizations-list [[{:keys [vcs_type]} :as users-and-orgs] selected-transfer-org owner]
  ;; split user-orgs from orgs and grab the first (and only) user-org
  ;; Note that user-login will be nil if the current org is the user-org
  (let [{[{user-login :login}] nil
         orgs true} (group-by :org users-and-orgs)
        sorted-org-names (cond->> orgs
                              true (map :login)
                              true (sort-by string/lower-case)
                              user-login (cons user-login))]
    [:div.controls.col-md-3
     [:h4 (str (utils/prettify-vcs_type vcs_type) " Organizations")]
     (for [org-name sorted-org-names
           :let [org-map {:org-name org-name
                          :vcs-type vcs_type}]]
       [:div.radio
        [:label {:name org-name}
         [:input {:value org-name
                  :checked (= selected-transfer-org
                              org-map)
                  :on-change #(raise! owner
                                      [:selected-transfer-org-updated
                                       {:org org-map}])
                  :type "radio"}]
         org-name]])]))

(defn transfer-organizations [{user-orgs :user-orgs
                               {org-name :name
                                :keys [vcs_type selected-transfer-org]} :current-org}
                              owner]
  (om/component
   (html
    (let [{gh-user-orgs "github"
           bb-user-orgs "bitbucket"} (->> user-orgs
                                          (remove #(and (= (:login %) org-name)
                                                        (= (:vcs_type %) vcs_type)))
                                          (group-by :vcs_type))
          selected-transfer-org-name (:org-name selected-transfer-org)]
      [:div.row-fluid
       [:div.span8
        [:fieldset
         [:legend "Transfer plan to a different organization"]
         [:div.alert.alert-warning
          [:strong "Warning!"]
          [:p "If you're not an admin on the "
           (if selected-transfer-org-name
             (str selected-transfer-org-name " organization,")
             "organization you transfer to,")
           " then you won't be able to transfer the plan back or edit the plan."]
          [:p
           "The transferred plan will be extended to include the "
           org-name " organization, so your builds will continue to run. Only admins of the "
           (if selected-transfer-org-name
             (str selected-transfer-org-name " org")
             "organization you transfer to")
           " will be able to edit the plan."]]
         (if-not user-orgs
           [:div "Loading organization list..."]
           [:div.container-fluid
            [:form
             [:div.row
              (when gh-user-orgs
                (transfer-organizations-list gh-user-orgs selected-transfer-org owner))
              (when bb-user-orgs
                (transfer-organizations-list bb-user-orgs selected-transfer-org owner))]
             [:div.row
              [:div.form-actions.span6
               (button/managed-button
                {:success-text "Transferred"
                 :loading-text "Transferring..."
                 :disabled? (not selected-transfer-org)
                 :kind :primary
                 :on-click #(raise! owner
                                    [:transfer-plan-clicked
                                     {:from-org {:org-name org-name
                                                 :vcs-type vcs_type}
                                      :to-org selected-transfer-org}])}
                "Transfer Plan")]]]])]]]))))

(defn organizations [app owner]
  (om/component
   (html
    [:div.organizations
     (om/build piggieback-organizations {:current-org (get-in app state/org-data-path)
                                         :user-orgs (get-in app state/user-organizations-path)})
     (om/build transfer-organizations {:current-org (get-in app state/org-data-path)
                                       :user-orgs (get-in app state/user-organizations-path)})])))

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
             [:div.row-fluid [:div.offset1.span6 (spinner)]]]
            [:div
              [:div.row-fluid [:legend.span8 "Card on file"]]
              [:div.row-fluid.space-below
               [:div.offset1.span6
                (om/build table/table
                          {:rows [card]
                           :key-fn (constantly "card")
                           :columns [{:header "Name"
                                      :cell-fn #(:name % "N/A")}
                                     {:header "Card type"
                                      :cell-fn #(:type % "N/A")}
                                     {:header "Card Number"
                                      :cell-fn #(if (contains? % :last4)
                                                  (str "xxxx-xxxx-xxxx-" (:last4 %))
                                                  "N/A")}
                                     {:header "Expiry"
                                      :cell-fn #(if (contains? % :exp_month)
                                                  (gstring/format "%02d/%s" (:exp_month %) (:exp_year %))
                                                  "N/A")}]})]]
              [:div.row-fluid
               [:div.offset1.span7
                [:form.form-horizontal
                 [:div.control-group
                  [:div.control
                   (button/managed-button
                    {:success-text "Success"
                     :failed-text "Failed"
                     :loading-text "Updating"
                     :on-click #(raise! owner [:update-card-clicked])
                     :kind :primary}
                    "Change Credit Card")]]]]]]))))))

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
    [:p (str "Your plan includes " discount-amount " off " discount-period " from coupon code ")
     [:strong id]]))

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
             [:div.row-fluid [:div.span8 (spinner)]]]
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
                 (button/managed-button
                  {:success-text "Saved invoice data"
                   :loading-text "Saving invoice data..."
                   :on-click #(raise! owner [:save-invoice-data-clicked])
                   :kind :primary}
                  "Save Invoice Data")]]]]]))))))

(defn- invoice-total
  [invoice]
  (/ (:amount_due invoice) 100))

(defn- stripe-ts->date
  [ts]
  (datetime/year-month-day-date (* 1000 ts)))

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
              (spinner)]]
            [:div.invoice-data.row-fluid
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
              (om/build table/table
                        {:rows invoices
                         :key-fn :id
                         :columns [{:header "Invoice date"
                                    :cell-fn (comp stripe-ts->date :date)}

                                   {:header "Time period covered"
                                    :cell-fn (comp str stripe-ts->date :period_start)}

                                   {:header "Total"
                                    :type :right
                                    :cell-fn #(gstring/format "$%.2f" (invoice-total %))}

                                   {:type :shrink
                                    :cell-fn
                                    (fn [invoice]
                                      (button/managed-button
                                       {:failed-text "Failed" ,
                                        :success-text "Sent" ,
                                        :loading-text "Sending..." ,
                                        :on-click #(raise! owner [:resend-invoice-clicked
                                                                  {:invoice-id (:id invoice)}])
                                        :size :medium
                                        :kind :secondary}
                                       "Resend"))}]})]]))))))

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
            vcs_type (get-in app state/org-vcs_type-path)
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
             "Please tell us why you're canceling. This helps us make CircleCI better!"]
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
                  (button/button
                   {:on-click #(om/set-state! owner [:show-errors?] true)
                    :kind :danger}
                   "Cancel Plan"))
                 (button/managed-button
                  {:kind :danger
                   :on-click #(raise! owner [:cancel-plan-clicked {:org-name org-name
                                                                   :vcs_type vcs_type
                                                                   :cancel-reasons reasons
                                                                   :cancel-notes notes}])}
                  "Cancel Plan")))]]])))))

(defn progress-bar [{:keys [max value]} owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:progress {:data-component `progress-bar
                   :value value
                   :max max}]))))

(defn osx-usage-table [{:keys [plan]} owner]
  (reify
    om/IRender
    (render [_]
      (let [org-name (:org_name plan)
            osx-max-minutes (some-> plan :osx :template :max_minutes)
            osx-usage (-> plan :usage :os:osx)]
        (html
          [:div.card {:data-component `osx-usage-table}
           [:div.header (str org-name "'s OS X usage")]
           [:hr.divider]
           (let [osx-usage (->> osx-usage
                                ;Remove any entries that do not have keys matching :yyyy_mm_dd.
                                ;This is to filter out the old style of keys which were :yyyy_mm.
                                (filterv (comp (partial re-matches #"\d{4}_\d{2}_\d{2}") name key))

                                ;Filter returns a vector of vectors [[key value] [key value]] so we
                                ;need to put them back into a map with (into {})
                                (into {})

                                ;Sort by key, which also happends to be billing period start date.
                                (sort)

                                ;Reverse the order so the dates decend
                                (reverse)

                                ;All we care about are the last 12 billing periods
                                (take 12)

                                ;Finally feed in the plan's max minutes
                                (map (fn [[_ usage-map]]
                                       {:usage usage-map
                                        :max osx-max-minutes})))]
             (if (and (not-empty osx-usage) osx-max-minutes)
               [:div
                (let [rows (for [{:keys [usage max]} osx-usage
                                 :let [{:keys [amount from to]} usage
                                       amount (.round js/Math (/ amount 1000 60))
                                       percent (.round js/Math (* 100 (/ amount max)))]]
                             {:from from
                              :to to
                              :max max
                              :amount amount
                              :percent percent
                              :over-usage? (> amount max)})]
                  (om/build table/table
                            {:rows rows
                             :key-fn (comp hash (juxt :from :to))
                             :columns [{:header "Billing Period"
                                        :type :shrink
                                        :cell-fn #(html
                                                   [:span
                                                    (datetime/month-name-day-date (:from %))
                                                    " - "
                                                    (datetime/month-name-day-date (:to %))])}
                                       {:header "Usage"
                                        :cell-fn #(om/build progress-bar {:max (:max %) :value (:amount %)})}
                                       {:type #{:right :shrink}
                                        :cell-fn #(html
                                                   [:span (when (:over-usage? %) {:class "over-usage"})
                                                    (:percent %) "%"])}
                                       {:type #{:right :shrink}
                                        :cell-fn #(html
                                                   [:span (when (:over-usage? %) {:class "over-usage"})
                                                    (.toLocaleString (:amount %)) "/" (.toLocaleString (:max %)) " minutes"])}]}))]
               [:div.explanation
                [:p "Looks like you haven't run any builds yet."]]))])))))

(defn osx-overview [{:keys [plan]} owner]
  (reify
    om/IRender
    (render [_]
      (let [{{plan-org-name :name
              plan-vcs-type :vcs_type} :org}
            plan]
        (html
         [:div
          [:h2 "OS X"]
          [:div
           [:p "Choose an OS X plan "
            [:a {:href (routes/v1-org-settings-path {:org plan-org-name
                                                     :vcs_type plan-vcs-type
                                                     :_fragment "osx-pricing"})} "here"] "."]
           (when (pm/osx? plan)
             (let [plan-name (some-> plan :osx :template :name)]
               [:div
                [:p
                 (cond
                   (pm/osx-trial-active? plan)
                   (gstring/format "You're currently on the OS X trial and have %s left. " (pm/osx-trial-days-left plan))

                   (and (pm/osx-trial-plan? plan)
                        (not (pm/osx-trial-active? plan)))
                   [:span "Your free trial of CircleCI for OS X has expired. Please "
                    [:a {:href (routes/v1-org-settings-path {:org plan-org-name
                                                             :vcs_type plan-vcs-type
                                                             :_fragment "osx-pricing"})} "select a plan"]" to continue building!"]

                   :else
                   (gstring/format "Your current OS X plan is %s ($%d/month). " plan-name (pm/osx-cost plan)))]
                (om/build osx-usage-table {:plan plan})]))]])))))

(defn overview [app owner]
  (om/component
   (html
    (let [org-name (get-in app state/org-name-path)
          vcs_type (get-in app state/org-vcs_type-path)
          {plan-org :org :as plan} (get-in app state/org-plan-path)
          plan-total (pm/stripe-cost plan)
          linux-container-cost (pm/linux-per-container-cost plan)
          price (-> plan :paid :template :price)
          containers (pm/linux-containers plan)
          piggiebacked? (pm/piggieback? plan org-name vcs_type)]
      [:div
       [:fieldset [:legend (str org-name "'s plan")]]
       [:div.explanation
        (when piggiebacked?
          [:p "This organization's projects will build under "
           [:a {:href (routes/v1-org-settings-path {:org (:name plan-org)
                                                    :vcs_type (:vcs_type plan-org)})}
            (:name plan-org) "'s plan."]])
        [:h2 "Linux"]
        (if (config/enterprise?)
          [:p "Your organization currently uses a maximum of " containers " containers. If your fleet size is larger than this, you should raise this to get access to your full capacity."]
          (cond (> containers 1)
                [:p (str "All Linux builds will be distributed across " containers " containers.")]
                (= containers 1)
                [:div
                 [:p (str org-name " is currently on the Hobbyist plan. Builds will run in a single, free container.")]
                 [:p "By " [:a {:href (routes/v1-org-settings-path {:org (:name plan-org)
                                                                    :vcs_type (:vcs_type plan-org)
                                                                    :_fragment "linux-pricing"})}
                            "upgrading"]
                  (str " " org-name "'s plan, " org-name " will gain access to concurrent builds, parallelism, engineering support, insights, build timings, and other cool stuff.")]]
                :else nil))
        (when (> (pm/trial-containers plan) 0)
          [:p
           (str (pm/trial-containers plan) " of these are provided by a trial. They'll be around for "
                (pluralize (pm/days-left-in-trial plan) "more day")
                ".")])
        (when (and (not (config/enterprise?))
                   (pm/linux? plan))
          [:p
           (str (pm/paid-linux-containers plan) " of these are paid")
           (if piggiebacked? ". "
               (list ", at $" (pm/current-linux-cost plan) "/month. "))
           (if (pm/grandfathered? plan)
             (list "We've changed our pricing model since this plan began, so its current price "
                   "is grandfathered in. "
                   "It would be $" (pm/linux-cost plan (pm/linux-containers plan)) " at current prices. "
                   "We'll switch it to the new model if you upgrade or downgrade. ")
             (list
              "You can "
              ;; make sure to link to the add-containers page of the plan's org,
              ;; in case of piggiebacking.
              [:a {:href (routes/v1-org-settings-path {:org (:name plan-org)
                                                       :vcs_type (:vcs_type plan-org)
                                                       :_fragment "linux-pricing"})}
               "add more"]
              (when-not piggiebacked?
                (list " at $" linux-container-cost " per container"))
              " for more parallelism and shorter queue times."))])
        (when (and (pm/freemium? plan) (> containers 1))
          [:p (str (pm/freemium-containers plan) " container is free.")])
        (when-not (config/enterprise?)
          [:div
           [:p "Additionally, projects that are public on GitHub will build with " pm/oss-containers " extra containers -- our gift to free and open source software."]
           (om/build osx-overview {:plan plan})])
        (when (config/enterprise?)
          (om/build linux-plan {:app app}))]]))))

(defn main-component []
  (merge
    {:overview overview
     :users users
     :projects projects
     :cancel cancel}
    (if (config/enterprise?)
      {:containers overview}
      {:containers cloud-pricing
       :osx-pricing cloud-pricing
       :linux-pricing cloud-pricing
       :organizations organizations
       :billing billing})))

(defn org-settings [app owner]
  (reify
    om/IRender
    (render [_]
      (let [org-data (get-in app state/org-data-path)
            vcs_type (:vcs_type org-data)
            subpage (or (get-in app state/org-settings-subpage-path)
                        :overview)
            plan (get-in app state/org-plan-path)]
        (html [:div.org-page
               (if-not (:loaded org-data)
                 (spinner)
                 [:div
                  (when (pm/suspended? plan)
                    (om/build project-common/suspended-notice {:plan plan :vcs_type vcs_type}))
                  (om/build common/flashes (get-in app state/error-message-path))
                  [:div#subpage
                   [:div
                    (if (:authorized? org-data)
                      (om/build (get (main-component) subpage projects) app)
                      [:div (om/build non-admin-plan
                                      {:login (get-in app [:current-user :login])
                                       :org-name (get-in app state/org-settings-org-name-path)
                                       :vcs_type (get-in app state/org-settings-vcs-type-path)
                                       :subpage subpage})])]]])])))))
