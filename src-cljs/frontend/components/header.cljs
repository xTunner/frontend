(ns frontend.components.header
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.analytics :as analytics]
            [frontend.config :as config]
            [frontend.components.build-head :as build-head]
            [frontend.components.common :as common]
            [frontend.components.crumbs :as crumbs]
            [frontend.components.forms :as forms]
            [frontend.components.instrumentation :as instrumentation]
            [frontend.components.license :as license]
            [frontend.components.statuspage :as statuspage]
            [frontend.models.project :as project-model]
            [frontend.models.feature :as feature]
            [frontend.models.plan :as plan]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [inspect]]
            [frontend.utils.github :refer [auth-url]]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils.html :refer [open-ext]]
            [frontend.components.insights.project :as insights-project]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn show-follow-project-button? [app]
  (when-let [project (get-in app state/project-path)]
    (and (not (:followed project))
         (= (vcs-url/org-name (:vcs_url project))
            (get-in app [:navigation-data :org]))
         (= (vcs-url/repo-name (:vcs_url project))
            (get-in app [:navigation-data :repo])))))

(defn show-settings-link? [app]
  (and
    (:read-settings (get-in app state/page-scopes-path))
    (not= false (-> app :navigation-data :show-settings-link?))))

(defn settings-link [app owner]
  (when (show-settings-link? app)
    (let [navigation-data (:navigation-data app)]
      (cond (:repo navigation-data) [:a.settings.project-settings
                                     {:href (routes/v1-project-settings-path navigation-data) }
                                     [:img.dashboard-icon {:src (common/icon-path "QuickLink-Settings")}]
                                     "Project Settings"]
            (:org navigation-data) [:a.settings.org-settings
                                    {:href (routes/v1-org-settings-path navigation-data)}
                                      [:img.dashboard-icon {:src (common/icon-path "QuickLink-Settings")}]
                                    "Organization Settings"]
            :else nil))))

(defn head-user [app owner]
  (reify
    om/IDisplayName (display-name [_] "User Header")
    om/IRender
    (render [_]
      (let [crumbs-data (get-in app state/crumbs-path)
            project (get-in app state/project-path)
            project-id (project-model/id project)
            project-name (:reponame project)
            project-user (:username project)
            vcs-type (:vcs-type project)
            vcs-url (:vcs_url project)]
        (html
          [:div.head-user
           [:ol.breadcrumb (crumbs/crumbs crumbs-data)]
           (when (show-follow-project-button? app)
             (forms/managed-button
               [:button#follow-project-button
                {:on-click #(raise! owner [:followed-project {:vcs-url vcs-url :project-id project-id}])
                 :data-spinner true}
                "follow the " (vcs-url/repo-name vcs-url) " project"]))
           (settings-link app owner)
           (when (= :project-settings (:navigation-point app))
             [:a.settings {:href (routes/v1-dashboard-path {:org project-user :repo project-name :vcs_type vcs-type})}
              (str "View " project-name " »")])
           (when (= :build (:navigation-point app))
             (om/build build-head/build-head-actions app))
           (when (= :project-insights (:navigation-point app))
             (om/build insights-project/header app))])))))

(defn head-admin [app owner]
  (reify
    om/IDisplayName (display-name [_] "Admin Header")
    om/IRender
    (render [_]
      (let [open? (get-in app state/show-admin-panel-path)
            expanded? (get-in app state/show-instrumentation-line-items-path)
            inspector? (get-in app state/show-inspector-path)
            user-session-settings (get-in app [:render-context :user_session_settings])
            env (config/env)
            local-storage-logging-enabled? (get-in app state/logging-enabled-path)]
        (html
          [:div
           [:div.environment {:class (str "env-" env)
                              :role "button"
                              :on-click #(raise! owner [:show-admin-panel-toggled])}
            env]
           [:div.head-admin {:class (concat (when open? ["open"])
                                            (when expanded? ["expanded"]))}
            [:div.admin-tools


             [:div.options
              [:a {:href "/admin/switch"} "switch "]
              [:a {:href "/admin/build-state"} "build state "]
              [:a {:href "/admin/recent-builds"} "builds "]
              [:a {:href "/admin/deployments"} "deploys "]
              (let [use-local-assets (get user-session-settings :use_local_assets)]
                [:a {:on-click #(raise! owner [:set-user-session-setting {:setting :use-local-assets
                                                                          :value (not use-local-assets)}])}
                 "local assets " (if use-local-assets "off " "on ")])
              (let [current-build-id (get user-session-settings :om_build_id "dev")]
                (for [build-id (remove (partial = current-build-id) ["dev" "whitespace" "production"])]
                  [:a.menu-item
                   {:on-click #(raise! owner [:set-user-session-setting {:setting :om-build-id
                                                                         :value build-id}])}
                   [:span (str "om " build-id " ")]]))
              [:a {:on-click #(raise! owner [:show-inspector-toggled])}
               (if inspector? "inspector off " "inspector on ")]
              [:a {:on-click #(raise! owner [:clear-instrumentation-data-clicked])} "clear stats"]
              [:a {:on-click #(do (raise! owner [:logging-enabled-clicked])
                                  nil)}
               (str (if local-storage-logging-enabled?
                      "turn OFF "
                      "turn ON ")
                    "logging-enabled?")]]
             (om/build instrumentation/summary (:instrumentation app))]
            (when (and open? expanded?)
              (om/build instrumentation/line-items (:instrumentation app)))]])))))

(defn maybe-active [current goal]
  {:class (when (= current goal)
            "active")})

(defn outer-subheader [nav-maps nav-point]
  (map
    #(when (-> %
               keys
               set
               (contains? nav-point))
       [:div.navbar.navbar-default.navbar-fixed-top.subnav
        [:div.container-fluid
         [:ul.nav.navbar-nav
          (for [[point {:keys [path title]}] %]
            [:li.list-item (maybe-active nav-point point)
             [:a.menu-item (open-ext {:href path}) title]])]]])
    nav-maps))

(defn outer-header [app owner]
  (reify
    om/IDisplayName (display-name [_] "Outer Header")
    om/IRender
    (render [_]
      (let [flash (get-in app state/flash-path)
            logged-in? (get-in app state/user-path)
            nav-point (:navigation-point app)
            hamburger-state (get-in app state/hamburger-menu-path)]
        (html
          [:div.outer-header
           [:div
            (when flash
              [:div#flash {:dangerouslySetInnerHTML {:__html flash}}])]
           [:div.navbar.navbar-default.navbar-fixed-top {:class (case nav-point
                                                                  :language-landing
                                                                  (get-in app [:navigation-data :language])
                                                                  :integrations
                                                                  (name (get-in app [:navigation-data :integration]))
                                                                  nil)
                                                         :on-touch-end #(raise! owner [:change-hamburger-state])}
            [:div.container-fluid
             [:div.hamburger-menu
              (condp = hamburger-state
                "closed" [:i.fa.fa-bars.fa-2x]
                "open" [:i.fa.fa-close.fa-2x])]
             [:div.navbar-header
              [:a#logo.navbar-brand
               {:href "/"}
               (common/circle-logo {:width nil
                                    :height 25})]
              (if logged-in?
                [:a.mobile-nav {:href "/dashboard"} "Back to app"]
                [:a.mobile-nav.signup (open-ext {:href "/signup/"}) "Sign up"])
              ]
             [:div.navbar-container {:class hamburger-state}
              [:ul.nav.navbar-nav
               (when (config/show-marketing-pages?)
                 (list
                   [:li.dropdown {:class (when (contains? #{:features
                                                            :mobile
                                                            :ios
                                                            :android
                                                            :integrations
                                                            :enterprise}
                                                          nav-point)
                                           "active")}
                    [:a.menu-item (open-ext {:href "/features/"})
                     "Product "
                     [:i.fa.fa-caret-down]]
                    [:ul.dropdown-menu
                     [:li {:role "presentation"}
                      [:a.sub.menu-item (
                                         merge
                                          (maybe-active nav-point :features)
                                         (open-ext {:role "menuitem"
                                                    :tabIndex "-1"
                                                    :href "/features/"}))
                       "Features"]]
                     [:li {:role "presentation"}
                      [:a.sub.menu-item (merge
                                          (maybe-active nav-point :mobile)
                                          (open-ext {:role "menuitem"
                                                     :tabIndex "-1"
                                                     :href "/mobile/"}))
                       "Mobile"]]
                     [:li {:role "presentation"}
                      [:a.sub.menu-item (merge
                                          (maybe-active nav-point :integrations)
                                          (open-ext {:role "menuitem"
                                                     :tabIndex "-1"
                                                     :href "/integrations/docker/"}))
                       "Docker"]]
                     [:li {:role "presentation"}
                      [:a.sub.menu-item (merge
                                          (maybe-active nav-point :enterprise)
                                          (open-ext {:role "menuitem"
                                                     :tabIndex "-1"
                                                     :href "/enterprise/"}))
                       "Enterprise"]]]]
                   [:li (maybe-active nav-point :pricing)
                    [:a.menu-item (open-ext {:href "/pricing/"}) "Pricing"]]))
               [:li (maybe-active nav-point :documentation)
                [:a.menu-item {:href "/docs"} "Documentation"]]
               [:li [:a.menu-item {:href "https://discuss.circleci.com" :target "_blank"} "Discuss"]]
               (when (config/show-marketing-pages?)
                 (list
                   [:li {:class (when (contains? #{:about
                                                   :contact
                                                   :team
                                                   :jobs
                                                   :press}
                                                 nav-point)
                                  "active")}
                    [:a.menu-item (open-ext {:href "/about/"}) "About Us"]]
                   [:li [:a.menu-item {:href "http://blog.circleci.com"} "Blog"]]))]
              (if logged-in?
                [:ul.nav.navbar-nav.navbar-right.back-to-app
                 [:li [:a.menu-item {:href "/dashboard"} "Back to app"]]]
                [:ul.nav.navbar-nav.navbar-right
                 [:li
                  [:a.login.login-link.menu-item {:href (auth-url)
                                                  :on-click #(raise! owner [:track-external-link-clicked {:event :login-clicked}])
                                                  :title "Log In with Github"}
                   "Log In"]]
                 [:li
                  (if (= :page (om/get-shared owner [:ab-tests :auth-button-vs-page]))
                    [:a.signup-link.btn.btn-success.navbar-btn.menu-item (open-ext {:href "/signup/"}) "Sign Up"]
                    [:a.signup-link.btn.btn-success.navbar-btn.menu-item
                     {:href (auth-url :destination "/dashboard")
                      :on-click #(raise! owner [:track-external-link-clicked
                                                {:event :oauth-authorize-clicked
                                                 :properties {:oauth-provider "github"}
                                                 :path (auth-url :destination "/dashboard")}])}
                     "Sign Up"])]])]]]
           (outer-subheader
             [{:mobile {:path "/mobile"
                        :title "Mobile"}
               :ios {:path "/mobile/ios"
                     :title "iOS"}
               :android {:path "/mobile/android"
                         :title "Android"}}
              {:about {:path "/about"
                       :title "Overview"}
               :team {:path "/about/team"
                      :title "Team"}
               :contact {:path "/contact"
                         :title "Contact Us"}
               :jobs {:path "/jobs"
                      :title "Jobs"}
               :press {:path "/press"
                       :title "Press"}}
              {:enterprise {:path "/enterprise"
                            :title "Overview"}
               :azure {:path "/enterprise/azure"
                       :title "Azure"}
               :aws {:path "/enterprise/aws"
                     :title "AWS"}}]
             nav-point)])))))

(defn osx-usage-warning-banner [plan owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div.alert.alert-warning {:data-component `osx-usage-warning-banner}
         [:div.usage-message
           [:span (str "Your current usage represents your "  (plan/current-months-osx-usage-% plan) "% of ")]
           [:a {:href (routes/v1-org-settings {:org (:org_name plan)})} "current OSX plan"]
           [:span ". Please "]
           [:a {:href (routes/v1-org-settings-subpage {:org (:org_name plan)
                                                       :subpage "containers"})}
            "upgrade"]
           [:span " or reach out to your account manager if you have questions about billing."]]
         [:a.dismiss {:on-click #(raise! owner [:dismiss-osx-usage-banner {:current-usage (plan/current-months-osx-usage-% plan)}])}
          [:i.material-icons "clear"]]]))))

(defn inner-header [app owner]
  (reify
    om/IDisplayName (display-name [_] "Inner Header")
    om/IRender
    (render [_]
      (let [admin? (if (config/enterprise?)
                     (get-in app [:current-user :dev-admin])
                     (get-in app [:current-user :admin]))
            logged-out? (not (get-in app state/user-path))
            license (get-in app state/license-path)
            project (get-in app state/project-path)
            plan (get-in app state/project-plan-path)]
        (html
          [:header.main-head (when logged-out? {:class "guest"})
           (when (license/show-banner? license)
             (om/build license/license-banner license))
           (when admin?
             (om/build head-admin app))
           (when (config/statuspage-header-enabled?)
             (om/build statuspage/statuspage app))
           (when logged-out?
             (om/build outer-header app))
           (when (and (feature/enabled? :ios-build-usage)
                      (= :build (:navigation-point app))
                      (project-model/feature-enabled? project :osx)
                      (plan/over-usage-threshold? plan plan/first-warning-threshold)
                      (plan/over-dismissed-level? plan (get-in app state/dismissed-osx-usage-level)))
             (om/build osx-usage-warning-banner plan))
           (when (seq (get-in app state/crumbs-path))
             (om/build head-user app))])))))


(defn header [app owner]
  (reify
    om/IDisplayName (display-name [_] "Header")
    om/IRender
    (render [_]
      (let [inner? (get-in app state/inner?-path)
            logged-in? (get-in app state/user-path)
            _ (utils/mlog "header render inner? " inner? " logged-in? " logged-in?)]
        (html
          (if inner?
            (om/build inner-header app)
            (om/build outer-header app)))))))
