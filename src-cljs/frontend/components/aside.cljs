(ns frontend.components.aside
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :refer [lower-case]]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.license :as license]
            [frontend.components.shared :as shared]
            [frontend.components.svg :refer [svg]]
            [frontend.config :as config]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.models.feature :as feature]
            [frontend.models.project :as project-model]
            [frontend.models.plan :as pm]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils.seq :refer [select-in]]
            [goog.style]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontend.utils.html :refer [open-ext]]
            [frontend.utils.launchdarkly :as ld])
  (:require-macros [frontend.utils :refer [html inspect]]))

(defn status-ico-name [build]
  (case (:status build)
    "running" :busy-light

    "success" :pass-light
    "fixed"   :pass-light

    "failed"   :fail-light
    "timedout" :fail-light

    "queued"      :hold-light
    "not_running" :hold-light
    "retried"     :hold-light
    "scheduled"   :hold-light

    "canceled"            :stop-light
    "no_tests"            :stop-light
    "not_run"             :stop-light
    "infrastructure_fail" :stop-light
    "killed"              :stop-light

    :none-light))

(defn sidebar-build [build {:keys [vcs_type org repo branch latest?]}]
  [:a.status {:class (when latest? "latest")
       :href (routes/v1-build-path vcs_type org repo (:build_num build))
       :title (str (build-model/status-words build) ": " (:build_num build))}
   (common/ico (status-ico-name build))])

(defn branch [data owner]
  (reify
    om/IDisplayName (display-name [_] "Aside Branch Activity")
    om/IRender
    (render [_]
      (let [{:keys [org repo branch-data vcs_type]} data
            [name-kw branch-builds] branch-data
            display-builds (take-last 5 (sort-by :build_num (concat (:running_builds branch-builds)
                                                                    (:recent_builds branch-builds))))]
        (html
         [:li
          [:div.branch
           {:role "button"}
           [:a {:href (routes/v1-dashboard-path {:org org
                                                 :repo repo
                                                 :branch (name name-kw)
                                                 :vcs_type vcs_type})
                :title (utils/display-branch name-kw)}
            (-> name-kw utils/display-branch (utils/trim-middle 23))]]
          [:div.statuses {:role "button"}
           (for [build display-builds]
             (sidebar-build build {:vcs_type vcs_type :org org :repo repo :branch (name name-kw)}))]])))))

(defn project-settings-link [{:keys [project]} owner]
  (when (and (project-model/can-write-settings? project))
    (let [org-name (:username project)
          repo-name (:reponame project)
          vcs-type (:vcs_type project)]
      [:a.project-settings-icon {:href (routes/v1-project-settings-path {:vcs_type vcs-type
                                                                         :org org-name
                                                                         :repo repo-name})
                                 :title  (str
                                           (project-model/project-name project)
                                           " settings")
                                 :on-click #((om/get-shared owner :track-event) {:event-type :project-settings-clicked
                                                                                 :properties {:org org-name
                                                                                              :repo repo-name}})}
       [:i.material-icons "settings"]])))

(defn branch-list [{:keys [branches show-all-branches? navigation-data]} owner {:keys [identities show-project?]}]
  (reify
    om/IDisplayName (display-name [_] "Aside Branch List")
    om/IRender
      (render [_]
        (let [branches-filter (if show-all-branches?
                                (constantly true)
                              (partial project-model/personal-branch? identities))]
          (html
           [:ul.branches
            (for [branch (filter branches-filter branches)]
              (let [project (:project branch)
                    latest-build (last (sort-by :build_num (concat (:running_builds branch)
                                                                   (:recent_builds branch))))
                    vcs-url (:vcs_url project)
                    org-name (project-model/org-name project)
                    repo-name (project-model/repo-name project)]
                [:li {:class (when (and (= org-name (:org navigation-data))
                                        (= repo-name (:repo navigation-data))
                                        (= (name (:identifier branch))
                                           (:branch navigation-data)))
                               "selected")}
                 [:a {:href (routes/v1-dashboard-path {:vcs_type (:vcs_type project)
                                                       :org (:username project)
                                                       :repo (:reponame project)
                                                       :branch (name (:identifier branch))})
                      :on-click #((om/get-shared owner :track-event) {:event-type :branch-clicked
                                                                      :properties {:repo repo-name
                                                                                   :org org-name
                                                                                   :branch (name (:identifier branch))}})}
                  [:.branch
                   [:.last-build-status
                    (om/build svg {:class "badge-icon"
                                   :src (-> latest-build build-model/status-icon common/icon-path)})]
                   [:.branch-info
                    (when show-project?
                      [:.project-name
                       {:title (project-model/project-name project)}
                       (project-model/project-name project)])
                    [:.branch-name
                     {:title (utils/display-branch (:identifier branch))}
                     (utils/display-branch (:identifier branch))]
                    (let [last-activity-time (project-model/most-recent-activity-time branch)]
                      [:.last-build-info
                       {:title (when last-activity-time
                                 (datetime/full-datetime (js/Date.parse last-activity-time)))}
                       (if last-activity-time
                         (list
                          (om/build common/updating-duration
                                    {:start last-activity-time}
                                    {:opts {:formatter datetime/time-ago}})
                          " ago")
                         "never")])]]]
                 (when show-project?
                   (project-settings-link {:project project} owner))]))])))))

(defn project-aside [{:keys [project show-all-branches? navigation-data expanded-repos]} owner {:keys [identities]}]
  (reify
    om/IDisplayName (display-name [_] "Aside Project")
    om/IRender
    (render [_]
      (let [repo (project-model/project-name project)
            vcs-url (:vcs_url project)
            org-name (project-model/org-name project)
            repo-name (project-model/repo-name project)]
        (html [:li
               [:.project-heading
                {:class (when (and (= org-name (:org navigation-data))
                                   (= repo-name (:repo navigation-data))
                                   (not (contains? navigation-data :branch)))
                          "selected")
                 :title (project-model/project-name project)}
                [:i.fa.rotating-chevron {:class (when (expanded-repos repo) "expanded")
                                         :on-click #(raise! owner [:expand-repo-toggled {:repo repo}])}]
                [:a.project-name {:href (routes/v1-project-dashboard-path {:vcs_type (:vcs_type project)
                                                                           :org (:username project)
                                                                           :repo (:reponame project)})
                                  :on-click #((om/get-shared owner :track-event) {:event-type :project-clicked
                                                                                  :properties {:org org-name
                                                                                               :repo repo-name}})}
                 (project-model/project-name project)]
                (project-settings-link {:project project} owner)]

               (when (expanded-repos repo)
                 (om/build branch-list
                           {:branches (->> project
                                           project-model/branches
                                           (sort-by (comp lower-case name :identifier)))
                            :show-all-branches? show-all-branches?
                            :navigation-data navigation-data}
                           {:opts {:identities identities}}))])))))

(defn expand-menu-items [items subpage]
  (for [item items]
    (case (:type item)

      :heading
      [:.aside-item.aside-heading
       (:title item)]

      :subpage
      [:a.aside-item {:href (:href item)
                      :class (when (= subpage (:subpage item)) "active")}
       (:title item)])))

(defn project-settings-nav-items [data owner]
  (let [navigation-data (:navigation-data data)
        project (get-in data state/project-path)
        feature-flags (project-model/feature-flags project)]
    (remove nil?
      [{:type :heading :title "Project Settings"}
       {:type :subpage :href "edit" :title "Overview" :subpage :overview}
       {:type :subpage :href (routes/v1-org-settings-path navigation-data) :title "Org Settings"
        :class "project-settings-to-org-settings"}
       {:type :heading :title "Build Settings"}
       ;; Both conditions are needed to handle special cases like non-enterprise stanging
       ;; instances that don't have OS X beta enabled.
       (when (or (not (config/enterprise?)) (contains? feature-flags :osx))
         {:type :subpage :href "#build-environment" :title "Build Environment" :subpage :build})
       (when (project-model/parallel-available? project)
         {:type :subpage :href "#parallel-builds" :title "Adjust Parallelism" :subpage :parallel-builds})
       {:type :subpage :href "#env-vars" :title "Environment Variables" :subpage :env-vars}
       {:type :subpage :href "#advanced-settings" :title "Advanced Settings" :subpage :advance}
       (when (or (feature/enabled? :project-cache-clear-buttons)
                 (config/enterprise?))
         {:type :subpage :href "#clear-caches" :title "Clear Caches" :subpage :clear-caches})
       {:type :heading :title "Test Commands"}
       {:type :subpage :href "#setup" :title "Dependency Commands" :subpage :setup}
       {:type :subpage :href "#tests" :title "Test Commands" :subpage :tests}
       {:type :heading :title "Notifications"}
       {:type :subpage :href "#hooks" :title "Chat Notifications" :subpage :hooks}
       {:type :subpage :href "#webhooks" :title "Webhook Notifications" :subpage :webhooks}
       {:type :subpage :href "#badges" :title "Status Badges" :subpage :badges}
       {:type :heading :title "Permissions"}
       {:type :subpage :href "#checkout" :title "Checkout SSH keys" :subpage :checkout}
       {:type :subpage :href "#ssh" :title "SSH Permissions" :subpage :ssh}
       {:type :subpage :href "#api" :title "API Permissions" :subpage :api}
       {:type :subpage :href "#aws" :title "AWS Permissions" :subpage :aws}
       (when (project-model/osx? project)
         {:type :subpage :href "#code-signing" :title "OS X Code Signing" :subpage :code-signing})
       {:type :heading :title "Continuous Deployment"}
       {:type :subpage :href "#heroku" :title "Heroku Deployment" :subpage :heroku}
       {:type :subpage :href "#aws-codedeploy" :title "AWS CodeDeploy" :subpage :aws-codedeploy}
       {:type :subpage :href "#deployment" :title "Other Deployments" :subpage :deployment}])))

(defn project-settings-menu [app owner]
  (reify
    om/IRender
    (render [_]
      (let [subpage (:project-settings-subpage app :overview)]
        (html
          [:div.aside-user {:class (when (= :project-settings (:navigation-point app)) "open")}
           [:a.close-menu {:href "./"} ; This may need to change if we drop hashtags from url structure
            (common/ico :fail-light)]
           [:div.aside-user-options
            (expand-menu-items (project-settings-nav-items app owner) subpage)]])))))

(defn org-settings-nav-items [plan {org-name :name
                                    org-vcs-type :vcs_type
                                    :as org-data}]
  (concat
   [{:type :heading :title "Plan"}
    {:type :subpage :title "Overview" :href "#" :subpage :overview}]
   (if-not (pm/can-edit-plan? plan org-name org-vcs-type)
     [{:type :subpage :href "#containers" :title "Add containers" :subpage :containers}]
     (concat
      [{:type :subpage :title "Update plan" :href "#containers" :subpage :containers}]
      (when (pm/transferrable-or-piggiebackable-plan? plan)
        [{:type :subpage :title "Organizations" :href "#organizations" :subpage :organizations}])
      (when (pm/stripe-customer? plan)
        [{:type :subpage :title "Billing info" :href "#billing" :subpage :billing}])))
   [{:type :heading :title "Organization"}
    {:type :subpage :href "#projects" :title "Projects" :subpage :projects}
    {:type :subpage :href "#users" :title "Users" :subpage :users}]))

(defn account-settings-nav-items []
  [{:type :subpage :href (routes/v1-account-subpage {:subpage "notifications"}) :title "Notifications" :subpage :notifications}
   {:type :subpage :href (routes/v1-account-subpage {:subpage "api"}) :title "API Tokens" :subpage :api}
   {:type :subpage :href (routes/v1-account-subpage {:subpage "heroku"}) :title "Heroku" :subpage :heroku}
   {:type :subpage :href (routes/v1-account-subpage {:subpage "plans"}) :title "Plan Pricing" :subpage :plans}
   {:type :subpage :href (routes/v1-account-subpage {:subpage "beta"}) :title "Beta Program" :subpage :beta}])

(defn account-settings-menu [app owner]
  (reify
    om/IRender
    (render [_]
      (let [subpage (:project-settings-subpage app :overview)]
        (html
          [:div.aside-user {:class (when (= :account (:navigation-point app)) "open")}
           [:header
            [:h4 "Account Settings"]
            [:a.close-menu {:href "./"} ; This may need to change if we drop hashtags from url structure
             (common/ico :fail-light)]]
           [:div.aside-user-options
            (expand-menu-items (account-settings-nav-items) subpage)]])))))

(defn admin-settings-nav-items []
  (filter
    identity
    [{:type :subpage :href "/admin" :title "Overview" :subpage nil}
     (when (config/enterprise?)
       {:type :subpage :href "/admin/management-console" :title "Management Console" :subpage nil})
     {:type :subpage :href "/admin/fleet-state" :title "Fleet State" :subpage :fleet-state}
     (when (config/enterprise?)
       {:type :subpage :href "/admin/license" :title "License" :subpage :license})
     (when (config/enterprise?)
       {:type :subpage :href "/admin/users" :title "Users" :subpage :users})
     (when (config/enterprise?)
       {:type :subpage :href "/admin/system-settings" :title "System Settings" :subpage :system-settings})]))

(defn admin-settings-menu [app owner]
  (reify
    om/IRender
    (render [_]
      (let [subpage (:project-settings-subpage app :overview)]
        (html
          [:div.aside-user {:class (when (= :admin-settings (:navigation-point app)) "open")}
           [:header
            [:h4 "Admin Settings"]
            [:a.close-menu {:href "./"} ; This may need to change if we drop hashtags from url structure
             (common/ico :fail-light)]]
           [:div.aside-user-options
            (expand-menu-items (admin-settings-nav-items) subpage)]])))))

(defn redirect-org-settings-subpage
  "Piggiebacked plans can't go to :containers, :organizations, :billing, or :cancel.
  Un-piggiebacked plans shouldn't be able to go to the old 'add plan' page. This function
  selects a different page for these cases."
  [subpage plan org-name vcs-type]
  (cond ;; Redirect :plan to :containers for paid plans that aren't piggiebacked.
        (and plan
             (pm/can-edit-plan? plan org-name vcs-type)
             (= subpage :plan))
        :containers

        ;; Redirect :organizations, :billing, and :cancel to the overview page
        ;; for piggiebacked plans.
        (and plan
             (not (pm/can-edit-plan? plan org-name vcs-type))
             (#{:organizations :billing :cancel} subpage))
        :overview

        :else subpage))

(defn org-settings-menu [app owner]
  (reify
    om/IRender
    (render [_]
      (let [plan (get-in app state/org-plan-path)
            org-data (get-in app state/org-data-path)
            subpage (redirect-org-settings-subpage (:project-settings-subpage app) plan (:name org-data) (:vcs_type org-data))
            items (org-settings-nav-items plan org-data)]
        (html
         [:div.aside-user {:class (when (= :org-settings (:navigation-point app)) "open")}
          [:header
           [:h4 "Organization Settings"]
           [:a.close-menu {:href "./"} ; This may need to change if we drop hashtags from url structure
            (common/ico :fail-light)]]
          [:div.aside-user-options
           (expand-menu-items items subpage)]])))))

(defn collapse-group-id [project]
  "Computes a hash of the project id.  Includes the :current-branch if
  available.  The hashing is performed because this data is stored on
  the client side and we don't want to leak data"
  (let [project-id (project-model/id project)
        branch (:current-branch project)]
    (utils/md5 (str project-id branch))))

(defn branch-activity-list [app owner opts]
  (reify
    om/IRender
    (render [_]
      (let [show-all-branches? (get-in app state/show-all-branches-path)
            expanded-repos (get-in app state/expanded-repos-path)
            sort-branches-by-recency? (get-in app state/sort-branches-by-recency-path)
            projects (get-in app state/projects-path)
            settings (get-in app state/settings-path)
            recent-projects-filter (if (and sort-branches-by-recency?
                                            (not show-all-branches?))
                                     (partial project-model/personal-recent-project? (:identities opts))
                                     identity)]
        (html
         [:div.aside-activity.open
          [:header
           [:select {:class "toggle-sorting"
                     :name "toggle-sorting"
                     :on-change #(raise! owner [:sort-branches-toggled
                                                (utils/parse-uri-bool (.. % -target -value))])
                     :value sort-branches-by-recency?}
            [:option {:value false} "By Repo"]
            [:option {:value true} "Recent"]]

           [:div.toggle-all-branches
            [:input {:id "my-branches"
                     :name "toggle-all-branches"
                     :type "radio"
                     :value "false"
                     :checked (not show-all-branches?)
                     :react-key "toggle-all-branches-my-branches"
                     :on-change #(raise! owner [:show-all-branches-toggled false])}]
            [:label {:for "my-branches"}
             "Mine"]
            [:input {:id "all-branches"
                     :name "toggle-all-branches"
                     :type "radio"
                     :value "true"
                     :checked show-all-branches?
                     :react-key "toggle-all-branches-all-branches"
                     :on-change #(raise! owner [:show-all-branches-toggled true])}]
            [:label {:for "all-branches"}
             "All"]]]

          (if sort-branches-by-recency?
            (om/build branch-list
                      {:branches (->> projects
                                      project-model/sort-branches-by-recency
                                      ;; Arbitrary limit on visible branches.
                                      (take 100))
                       :show-all-branches? show-all-branches?
                       :navigation-data (:navigation-data app)}
                      {:opts {:identities (:identities opts)
                              :show-project? true}})
            [:ul.projects
             (for [project (sort project-model/sidebar-sort projects)]
               (om/build project-aside
                         {:project project
                          :show-all-branches? show-all-branches?
                          :expanded-repos expanded-repos
                          :navigation-data (:navigation-data app)}
                         {:react-key (project-model/id project)
                          :opts {:identities (:identities opts)}}))])])))))

(defn aside-menu [app owner opts]
  (reify
    om/IDisplayName (display-name [_] "Aside Menu")
    om/IInitState (init-state [_] {:scrollbar-width 0})
    om/IDidMount (did-mount [_] (om/set-state! owner :scrollbar-width (goog.style/getScrollbarWidth)))
    om/IRender
    (render [_]
      (html
       [:nav.aside-left-menu
        (om/build project-settings-menu app)
        (om/build org-settings-menu app)
        (om/build admin-settings-menu app)
        (om/build account-settings-menu app)
        (om/build branch-activity-list app {:opts {:identities (:identities opts)
                                                   :scrollbar-width (om/get-state owner :scrollbar-width)}})]))))

(defn aside-nav [app owner]
  (reify
    om/IDisplayName (display-name [_] "Aside Nav")
    om/IDidMount
    (did-mount [_]
      (utils/tooltip ".aside-item"))
    om/IRender
    (render [_]
      (let [user (get-in app state/user-path)
            avatar-url (gh-utils/make-avatar-url user)]

        (html
          [:nav.aside-left-nav
           ;; TODO -ac Remove this class when ff removed
           {:class (when (ld/feature-on? "top-bar-ui-v-1") "top-bar-ff")}
           (when (not (ld/feature-on? "top-bar-ui-v-1"))
           [:a.aside-item.logo {:title "Dashboard"
                                :data-placement "right"
                                :data-trigger "hover"
                                :href (routes/v1-dashboard-path {})}
             [:div.logomark
              (common/ico :logo)]])

           [:a.aside-item {:data-placement "right"
                           :data-trigger "hover"
                           :title "Builds"
                           :href (routes/v1-dashboard-path {})}
            [:i.material-icons "storage"]
            [:div.nav-label "Builds"]]

           [:a.aside-item {:data-placement "right"
                            :data-trigger "hover"
                            :title "Insights"
                            :href "/build-insights"}
             [:i.material-icons "assessment"]
             [:div.nav-label "Insights"]]

           (if (feature/enabled? :projects-page)
             [:a.aside-item {:title "Projects"
                             :data-placement "right"
                             :data-trigger "hover"
                             :href "/projects"}
              [:i.material-icons "book"]
              [:div.nav-label "Projects"]]

             [:a.aside-item {:href "/add-projects",
                             :data-placement "right"
                             :data-trigger "hover"
                             :title "Add Projects"}
              [:i.material-icons "library_add"]
              [:div.nav-label "Add Projects"]])

           (if (feature/enabled? :team-page)
             [:a.aside-item {:href "/team",
                             :data-placement "right"
                             :data-trigger "hover"
                             :title "Team"}
              [:i.material-icons "group"]
              [:div.nav-label "Team"]]

             [:a.aside-item {:href "/invite-teammates",
                             :data-placement "right"
                             :data-trigger "hover"
                             :title "Add Teammates"}
              [:i.material-icons "group_add"]
              [:div.nav-label "Team"]])

            [:hr]

           (when (not (ld/feature-on? "top-bar-ui-v-1"))
             [:a.aside-item (open-ext {:title "Documentation"
                                       :data-placement "right"
                                       :data-trigger "hover"
                                       :href "https://circleci.com/docs/"})
              [:i.material-icons "description"]
              [:div.nav-label "Docs"]])

           (when (not (ld/feature-on? "top-bar-ui-v-1"))
             [:a.aside-item (merge (common/contact-support-a-info owner)
                                   {:title "Support"
                                    :data-placement "right"
                                    :data-trigger "hover"
                                    :data-bind "tooltip: {title: 'Support', placement: 'right', trigger: 'hover'}"})
              [:i.material-icons "chat"]
              [:div.nav-label "Support"]])

           (when (not (ld/feature-on? "top-bar-ui-v-1"))
             (when-not (config/enterprise?)
               [:a.aside-item (open-ext {:data-placement "right"
                                         :data-trigger "hover"
                                         :title "Changelog"
                                         :href "/changelog"})
                [:i.material-icons "receipt"]
                [:div.nav-label "Changelog"]]))

            [:hr]

            (when (:admin user)
              [:a.aside-item {:data-placement "right"
                              :data-trigger "hover"
                              :title "Admin"
                              :href "/admin"}
                [:i.material-icons "build"]
                [:div.nav-label "Admin"]])

           (when (not (ld/feature-on? "top-bar-ui-v-1"))
            [:a.aside-item.push-to-bottom {:data-placement "right"
                                           :data-trigger "hover"
                                           :title "Logout"
                                           :href "/logout"}
              [:i.material-icons "power_settings_new"]
              [:div.nav-label "Logout"]])])))))

(defn aside [app owner]
  (reify
    om/IDisplayName (display-name [_] "Aside")
    om/IRender
    (render [_]
      (let [user (get-in app state/user-path)
            identities (:identities user)
            avatar-url (gh-utils/make-avatar-url user)
            show-aside-menu? (get-in app [:navigation-data :show-aside-menu?] true)
            license (get-in app state/license-path)]
        (html
         [:aside.app-aside {:class (cond-> []
                                     (not show-aside-menu?) (conj "menuless"))}
          (when show-aside-menu?
            (om/build aside-menu app {:opts {:identities identities}}))])))))
