(ns frontend.components.header
  (:require [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.license :as license]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.page-header :as page-header]
            [frontend.components.pieces.top-banner :as top-banner]
            [frontend.components.svg :as svg]
            [frontend.config :as config]
            [frontend.models.feature :as feature]
            [frontend.models.plan :as plan]
            [frontend.models.project :as project-model]
            [frontend.notifications :as n]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils]
            [frontend.utils.github :refer [auth-url]]
            [frontend.utils.html :refer [open-ext]]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn show-follow-project-button? [app]
  (when-let [project (get-in app state/project-path)]
    (and (not (:followed project))
         (= (vcs-url/org-name (:vcs_url project))
            (get-in app [:navigation-data :org]))
         (= (vcs-url/repo-name (:vcs_url project))
            (get-in app [:navigation-data :repo])))))

(defn follow-project-button [project owner]
  (reify
    om/IRender
    (render [_]
      (let [project-id (project-model/id project)
            vcs-url (:vcs_url project)
            repo-name (vcs-url/repo-name vcs-url)]
        (button/managed-button
         {:on-click #(do
                      (raise! owner [:followed-project {:vcs-url vcs-url :project-id project-id}])
                      ((om/get-shared owner :track-event) {:event-type :follow-project-clicked
                                                           :properties {:vcs-url vcs-url
                                                                        :component "header"}}))
          :loading-text "Following..."
          :failed-text "Failed to follow"
          :success-text "Followed"
          :kind :primary
          :size :medium}
          "Follow Project")))))

(defn show-settings-link? [app]
  (and
    (:read-settings (get-in app state/page-scopes-path))
    (not= false (-> app :navigation-data :show-settings-link?))))

(defn settings-link [app owner]
  (let [{:keys [repo org] :as navigation-data} (:navigation-data app)]
    (cond repo (when (:write-settings (get-in app state/project-scopes-path))
                 [:div.btn-icon
                  [:a.header-settings-link.project-settings
                   {:href (routes/v1-project-settings-path navigation-data)
                    :title "Project settings"}
                   [:i.material-icons "settings"]]])
          org [:div.btn-icon
               [:a.header-settings-link.org-settings
                {:href (routes/v1-org-settings-path navigation-data)
                 :on-click #((om/get-shared owner :track-event) {:event-type :org-settings-link-clicked
                                                                 :properties {:org org
                                                                              :component "header"}})
                 :title "Organization settings"}
                [:i.material-icons "settings"]]]
          :else nil)))

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
                [:a.mobile-nav.signup
                 (open-ext {:href (if (config/enterprise?)
                                    (auth-url)
                                    "/signup/")})
                 "Sign up"])]
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
                [:a.menu-item {:href "https://circleci.com/docs/"}
                 (if (config/enterprise?)
                  "CircleCI Documentation"
                  "Documentation")]]
               (if (config/enterprise?)
                 [:li [:a.menu-item {:href "https://enterprise-docs.circleci.com"} "Enterprise Documentation"]])
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
                                                  :on-click #((om/get-shared owner :track-event) {:event-type :login-clicked})
                                                  :title "Log In with Github"}
                   "Log In"]]
                 (when (not (config/enterprise?))
                   [:li
                    [:a.signup-link.btn.btn-success.navbar-btn.menu-item (open-ext {:href "/signup/"
                                                                                    :on-click #((om/get-shared owner :track-event) {:event-type :signup-clicked})}) "Sign Up"]])])]]]
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
      (let [{{plan-org-name :name
              plan-vcs-type :vcs_type} :org} plan]
        (html
         [:div.alert.alert-warning {:data-component `osx-usage-warning-banner}
          [:div.usage-message
           [:div.icon (om/build svg/svg {:src (common/icon-path "Info-Warning")})]
           [:div.text
            [:span "Your current usage represents "]
            [:span.usage (plan/current-months-osx-usage-% plan)]
            [:span "% of your "]
            [:a.plan-link
             {:href (routes/v1-org-settings-path {:org plan-org-name
                                                  :vcs_type plan-vcs-type})} "current macOS plan"]
            [:span ". Please "]
            [:a.plan-link {:href (routes/v1-org-settings-path {:org plan-org-name
                                                               :vcs_type plan-vcs-type
                                                               :_fragment "osx-pricing"})}
             "upgrade"]
            [:span " or reach out to your account manager if you have questions about billing."]
            [:span " See overage rates "]
            [:a.plan-link {:href (routes/v1-org-settings-path {:org plan-org-name
                                                               :vcs_type plan-vcs-type
                                                               :_fragment "osx-pricing"})}
             "here."]]]
          [:a.dismiss {:on-click #(raise! owner [:dismiss-osx-usage-banner {:current-usage (plan/current-months-osx-usage-% plan)}])}
           [:i.material-icons "clear"]]])))))

(defn trial-offer-banner [app owner]
  (let [event-data {:plan-type :paid
                    :template :t3
                    :org (get-in app state/project-plan-org-path)}]
    (reify
      om/IDidMount
      (did-mount [_]
        ((om/get-shared owner :track-event) {:event-type :trial-offer-banner-impression}))
      om/IRender
      (render [_]
        (html
          [:div.alert.offer
           [:div.text
            [:div "Projects utilizing more containers generally have faster builds and less queueing. "
             [:a {:on-click #(raise! owner [:activate-plan-trial event-data])}
              "Click here "]
             "to activate a free two-week trial of 3 additional linux containers."]]
           [:a.dismiss {:on-click #(raise! owner [:dismiss-trial-offer-banner event-data])}
            [:i.material-icons "clear"]]])))))

(defn inner-header [{:keys [app crumbs actions]} owner]
  (reify
    om/IDisplayName (display-name [_] "Inner Header")
    om/IRender
    (render [_]
      (let [logged-out? (not (get-in app state/user-path))
            license (get-in app state/license-path)
            project (get-in app state/project-path)
            plan (get-in app state/project-plan-path)
            show-web-notif-banner? (not (get-in app state/remove-web-notification-banner-path))
            show-web-notif-banner-follow-up? (not (get-in app state/remove-web-notification-confirmation-banner-path))]
        (html
         [:header.main-head (when logged-out? {:class "guest"})
          (when (license/show-banner? license)
            (om/build license/license-banner license))
          (when logged-out?
            (om/build outer-header app))
          (when (and (= :build (:navigation-point app))
                     (project-model/feature-enabled? project :osx))
            (list
             (when (and (plan/osx? plan)
                        (plan/over-usage-threshold? plan plan/first-warning-threshold)
                        (plan/over-dismissed-level? plan (get-in app state/dismissed-osx-usage-level)))
               (om/build osx-usage-warning-banner plan))))
          (when (and (not (plan/trial? plan))
                     (= :build (:navigation-point app))
                     (not (project-model/oss? project))
                     (plan/admin? plan)
                     (feature/enabled? :offer-linux-trial)
                     (not (get-in app state/dismissed-trial-offer-banner)))
            (om/build trial-offer-banner app))
          (when (:build (get-in app state/build-data-path))
            (cond
              (and (= (n/notifications-permission) "default")
                   show-web-notif-banner?)
              (om/build top-banner/banner
                        {:banner-type "warning"
                         :content [:div
                                   [:span.banner-alert-icon
                                    [:img {:src (common/icon-path "Info-Info")}]]
                                   [:b "  New: "] "You can now get web notifications when your build is done! "
                                   [:a
                                    {:href "#"
                                     :on-click #(n/request-permission
                                                 (fn [response]
                                                   (raise! owner [:set-web-notifications-permissions {:enabled? (= response "granted")
                                                                                                      :response response}])))}
                                    "Click here to activate web notifications."]]
                         :impression-event-type :web-notifications-permissions-banner-impression
                         :dismiss-fn #(raise! owner [:dismiss-web-notifications-permissions-banner {:response (n/notifications-permission)}])})
              (and (not show-web-notif-banner?)
                   show-web-notif-banner-follow-up?)
              (om/build top-banner/banner
                        (let [response (n/notifications-permission)]
                          {:banner-type (case (n/notifications-permission)
                                          "default" "danger"
                                          "denied" "danger"
                                          "granted" "success")
                           :content [:div (let [not-granted-message "If you change your mind you can go to this link to turn web notifications on: "]
                                            (case (n/notifications-permission)
                                              "default" not-granted-message
                                              "denied"  not-granted-message
                                              "granted" "Thanks for turning on web notifications! If you want to change settings go to: "))
                                     [:a {:on-click #(raise! owner [:web-notifications-confirmation-account-settings-clicked {:response response}])}
                                      "Account Notifications"]]
                           :dismiss-fn #(raise! owner [:dismiss-web-notifications-confirmation-banner])}))))
          (when (seq crumbs)
            (om/build page-header/header {:crumbs crumbs
                                          :actions (cond-> []
                                                     (show-settings-link? app)
                                                     (conj (settings-link app owner))

                                                     true
                                                     (conj actions)

                                                     (show-follow-project-button? app)
                                                     (conj (om/build follow-project-button project)))}))])))))

(defn header [{:keys [app crumbs actions] :as props} owner]
  (reify
    om/IDisplayName (display-name [_] "Header")
    om/IRender
    (render [_]
      (if (#{:landing :error} (:navigation-point app))
        (om/build outer-header app)
        (om/build inner-header props)))))
