(ns frontend.components.app
  (:require [frontend.async :refer [raise!]]
            [frontend.components.app.legacy :as legacy]
            [frontend.components.aside :as aside]
            [frontend.components.common :as common]
            [frontend.components.enterprise-landing :as enterprise-landing]
            [frontend.components.inspector :as inspector]
            [frontend.components.instrumentation :as instrumentation]
            [frontend.components.pages.projects :as projects]
            [frontend.components.pieces.flash-notification :as flash]
            [frontend.components.pieces.topbar :as topbar]
            [frontend.components.statuspage :as statuspage]
            [frontend.config :as config]
            [frontend.state :as state]
            [frontend.utils.launchdarkly :as ld]
            [frontend.utils.legacy :refer [build-legacy]]
            [frontend.utils.seq :refer [dissoc-in]]
            [goog.dom :as gdom]
            [om.core :as om :include-macros true]
            [om.next :as om-next :refer-macros [defui]]
            [compassus.core :as compassus])
  (:require-macros [frontend.utils :refer [html]]))

(defui ^:once Loading
  ;; This component should actually not implement IQuery and have no query.
  ;; However, this will break Compassus until
  ;; https://github.com/compassus/compassus/issues/5's fix,
  ;; https://github.com/compassus/compassus/commit/584551699346ba62c47c96e5ae342d5e8a73be75
  ;; is released. Until then, ask for a key which the parser knows to ignore.
  static om-next/IQuery
  (query [this] '[:nothing/nothing])
  Object
  (render [this] nil))

(def routes
  {:route/loading (compassus/index-route Loading)
   :route/legacy-page legacy/LegacyPage
   :route/projects projects/Page})

(defn head-admin [app owner]
  (reify
    om/IDisplayName (display-name [_] "Admin Header")
    om/IRender
    (render [_]
      (let [open? (get-in app state/show-admin-panel-path)
            expanded? (get-in app state/show-instrumentation-line-items-path)
            inspector? (get-in app state/show-inspector-path)
            user-session-settings (get-in app [:render-context :user_session_settings])
            env (config/env)]
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
             [:a {:on-click #(raise! owner [:clear-instrumentation-data-clicked])} "clear stats"]]
            (om/build instrumentation/summary (:instrumentation app))]
           (when (and open? expanded?)
             (om/build instrumentation/line-items (:instrumentation app)))]])))))

(defn blocked-page [app]
  (let [reason (get-in app [:enterprise :site-status :blocked_reason])]
    (html
     [:div.outer {:style {:padding-top "0"}}
      [:div.enterprise-landing
       [:div.jumbotron
        common/language-background-jumbotron
        [:section.container
         [:div.row
          [:article.hero-title.center-block
           [:div.text-center (enterprise-landing/enterprise-logo)]
           [:h1.text-center "Error Launching CircleCI"]]]]
        [:div.row.text-center
         [:h2 reason]]]
       [:div.outer-section]]])))

(defn app-blocked? [app]
  (and (config/enterprise?)
       (= "blocked" (get-in app [:enterprise :site-status :status]))))

(defui ^:once Wrapper
  Object
  (render [this]
    (let [{:keys [factory props owner]} (om-next/props this)
          app (:legacy/state props)]
      (if (app-blocked? app)
        (blocked-page app)
        (let [user (get-in app state/user-path)
              admin? (if (config/enterprise?)
                       (get-in app [:current-user :dev-admin])
                       (get-in app [:current-user :admin]))
              show-inspector? (get-in app state/show-inspector-path)
              ;; :landing is still used by Enterprise. It and :error are
              ;; still "outer" pages.
              outer? (contains? #{:landing :error} (:navigation-point app))]
          (html
           [:div {:class (if outer? "outer" "inner")
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
              (build-legacy head-admin app))

            (when show-inspector?
              (inspector/inspector))

            (when (config/statuspage-header-enabled?)
              (build-legacy statuspage/statuspage app))

            [:.top
             [:.bar
              (when (ld/feature-on? "top-bar-ui-v-1")
                (build-legacy topbar/topbar {:user user}))]
             [:.flash-presenter
              (flash/presenter {:display-timeout 2000
                                :notification
                                (when-let [{:keys [number message]} (get-in app state/flash-notification-path)]
                                  (flash/flash-notification {:react-key number} message))})]]

            [:.below-top
             (when (and (not outer?) user)
               (let [compassus-route (compassus/current-route owner)
                     current-route (if (= :route/legacy-page compassus-route)
                                     (:navigation-point app)
                                     compassus-route)]
                 (when (not (ld/feature-on? "top-bar-ui-v-1"))
                   (build-legacy aside/aside-nav {:user user :current-route current-route}))))

             (factory props)]]))))))

(def wrapper (om-next/factory Wrapper))
