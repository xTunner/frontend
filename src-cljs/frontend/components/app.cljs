(ns frontend.components.app
  (:require [frontend.api :as api]
            [frontend.async :refer [raise!]]
            [frontend.components.account :as account]
            [frontend.components.admin :as admin]
            [frontend.components.aside :as aside]
            [frontend.components.common :as common]
            [frontend.components.dashboard :as dashboard]
            [frontend.components.enterprise-landing :as enterprise-landing]
            [frontend.components.errors :as errors]
            [frontend.components.insights :as insights]
            [frontend.components.inspector :as inspector]
            [frontend.components.instrumentation :as instrumentation]
            [frontend.components.invites :as invites]
            [frontend.components.landing :as landing]
            [frontend.components.org-settings :as org-settings]
            [frontend.components.pages.add-projects :as add-projects]
            [frontend.components.pages.build :as build]
            [frontend.components.pages.project-insights :as project-insights]
            [frontend.components.pages.project-settings :as project-settings]
            [frontend.components.pages.projects :as projects]
            [frontend.components.pages.team :as team]
            [frontend.components.pieces.flash-notification :as flash]
            [frontend.components.pieces.topbar :as topbar]
            [frontend.components.templates.main :as main-template]
            [frontend.config :as config]
            [frontend.models.feature :as feature]
            [frontend.state :as state]
            [frontend.utils :as utils]
            [frontend.utils.launchdarkly :as ld]
            [frontend.utils.seq :refer [dissoc-in]]
            [goog.dom :as gdom]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn templated
  "Takes an old-world \"dominant component\" function and returns a new-world
  page function, which builds a page using the main page template."
  [old-world-dominant-component-f]
  (fn [app owner]
    (reify
      om/IRender
      (render [_]
        (om/build main-template/template {:app app
                                          :main-content (om/build old-world-dominant-component-f app)})))))

(def nav-point->page
  (merge
   ;; Page component functions, which are good as they are.
   {:build build/page
    :project-settings project-settings/page
    :project-insights project-insights/page
    :add-projects add-projects/page
    :projects projects/page
    :team team/page}
   ;; Old-World dominant component functions which need to be wrapped in the `main` template.
   ;; As we migrate these, we'll move them into the map above.
   (into {}
         (map #(vector (key %) (templated (val %))))
         {:dashboard dashboard/dashboard
          :build-insights insights/build-insights
          :org-settings org-settings/org-settings
          :account account/account

          :admin-settings admin/admin-settings
          :build-state admin/build-state
          :switch admin/switch

          :landing (if (config/enterprise?) enterprise-landing/home landing/home)

          :error errors/error-page})))

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
                   {:key build-id
                    :on-click #(raise! owner [:set-user-session-setting {:setting :om-build-id
                                                                         :value build-id}])}
                   [:span (str "om " build-id " ")]]))
              [:a {:on-click #(raise! owner [:show-inspector-toggled])}
               (if inspector? "inspector off " "inspector on ")]
              [:a {:on-click #(raise! owner [:clear-instrumentation-data-clicked])} "clear stats"]
              [:a {:on-click #(raise! owner [:logging-enabled-clicked])}
               (str (if local-storage-logging-enabled?
                      "turn OFF "
                      "turn ON ")
                    "logging-enabled?")]]
             (om/build instrumentation/summary (:instrumentation app))]
            (when (and open? expanded?)
              (om/build instrumentation/line-items (:instrumentation app)))]])))))

(defn blocked-page [app owner]
  (let [reason (get-in app [:enterprise :site-status :blocked_reason])]
    (html
     [:div.outer {:style {:padding-top "0"}}
      [:div.enterprise-landing
       [:div.jumbotron
        common/language-background-jumbotron
        [:section.container
         [:div.row
          [:article.hero-title.center-block
           [:div.text-center frontend.components.enterprise-landing/enterprise-logo]
           [:h1.text-center "Error Launching CircleCI"]]]]
        [:div.row.text-center
         [:h2 reason]]]
       [:div.outer-section]]])))

(defn app-blocked? [app]
  (and (config/enterprise?)
       (= "blocked" (get-in app [:enterprise :site-status :status]))))

(defn app* [app owner {:keys [reinstall-om!]}]
  (reify
    om/IDisplayName (display-name [_] "App")
    om/IRender
    (render [_]
      (if (app-blocked? app)
        (blocked-page app owner)
        (when (:navigation-point app)
          (let [logged-in? (get-in app state/user-path)
                admin? (if (config/enterprise?)
                         (get-in app [:current-user :dev-admin])
                         (get-in app [:current-user :admin]))
                show-inspector? (get-in app state/show-inspector-path)
                ;; simple optimzation for real-time updates when the build is running
                app-without-container-data (dissoc-in app state/container-data-path)
                page (nav-point->page (:navigation-point app))]
            (html
             (let [inner? (get-in app state/inner?-path)]

               [:div {:class (if inner? "inner" "outer")
                      ;; Disable natural form submission. This keeps us from having to
                      ;; .preventDefault every submit button on every form.
                      ;;
                      ;; To let a button actually submit a form naturally, handle its click
                      ;; event and call .stopPropagation on the event. That will stop the
                      ;; event from bubbling to here and having its default behavior
                      ;; prevented.
                      :on-click #(let [target (.-target %)
                                       button (if (or (= (.-tagName target) "BUTTON")
                                                      (and (= (.-tagName target) "INPUT")
                                                           (= (.-type target) "submit")))
                                                ;; If the clicked element was a button or an
                                                ;; input.submit, that's the button.
                                                target
                                                ;; Otherwise, it's the button (if any) that
                                                ;; contains the clicked element.
                                                (gdom/getAncestorByTagNameAndClass target "BUTTON"))]
                                   ;; Finally, if we found an applicable button and that
                                   ;; button is associated with a form which it would submit,
                                   ;; prevent that submission.
                                   (when (and button (.-form button))
                                     (.preventDefault %)))}
                (when admin?
                  (om/build head-admin app))

                (when show-inspector?
                  (om/build inspector/inspector app))

                [:.top
                 [:.bar
                  (when (ld/feature-on? "top-bar-ui-v-1")
                    (topbar/topbar {:support-info (common/contact-support-a-info owner)
                                    :user (get-in app state/user-path)}))]
                 [:.flash-presenter
                  (flash/presenter {:display-timeout 2000
                                    :notification
                                    (when-let [{:keys [number message]} (get-in app state/flash-notification-path)]
                                      (flash/flash-notification {:react-key number} message))})]]

                [:.below-top
                 (when (and inner? logged-in?)
                   [:.left-nav
                    (om/build aside/aside-nav (dissoc app-without-container-data :current-build-data))])

                 [:.main
                  (om/build page app)]]]))))))))


(defn app [app owner opts]
  (reify
    om/IDisplayName (display-name [_] "App Wrapper")
    om/IRender (render [_] (om/build app* (dissoc app :inputs :state-map) {:opts opts}))))
