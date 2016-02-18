(ns frontend.components.app
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.components.account :as account]
            [frontend.components.admin :as admin]
            [frontend.components.aside :as aside]
            [frontend.components.build :as build-com]
            [frontend.components.dashboard :as dashboard]
            [frontend.components.documentation :as docs]
            [frontend.components.features :as features]
            [frontend.components.mobile :as mobile]
            [frontend.components.press :as press]
            [frontend.components.add-projects :as add-projects]
            [frontend.components.insights :as insights]
            [frontend.components.insights.project :as project-insights]
            [frontend.components.invites :as invites]
            [frontend.components.enterprise-landing :as enterprise-landing]
            [frontend.components.errors :as errors]
            [frontend.components.footer :as footer]
            [frontend.components.header :as header]
            [frontend.components.inspector :as inspector]
            [frontend.components.key-queue :as keyq]
            [frontend.components.placeholder :as placeholder]
            [frontend.components.pricing :as pricing]
            [frontend.components.project-settings :as project-settings]
            [frontend.components.shared :as shared]
            [frontend.components.landing :as landing]
            [frontend.components.org-settings :as org-settings]
            [frontend.components.common :as common]
            [frontend.components.top-nav :as top-nav]
            [frontend.components.signup :as signup]
            [frontend.api :as api]
            [frontend.config :as config]
            [frontend.instrumentation :as instrumentation]
            [frontend.models.feature :as feature]
            [frontend.models.project :as project]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.seq :refer [dissoc-in]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [ankha.core :as ankha])
  (:require-macros [frontend.utils :refer [html]]))

(def keymap
  (atom nil))

(defn loading [app owner]
  (reify
    om/IRender
    (render [_] (html [:div.loading-spinner common/spinner]))))

(defn dominant-component [app-state owner]
  (case (:navigation-point app-state)
    :build build-com/build
    :dashboard dashboard/dashboard
    :add-projects add-projects/add-projects
    :build-insights insights/build-insights
    :project-insights project-insights/project-insights
    :invite-teammates invites/teammates-invites
    :project-settings project-settings/project-settings
    :org-settings org-settings/org-settings
    :account account/account

    :admin-settings admin/admin-settings
    :build-state admin/build-state
    :switch admin/switch

    :loading loading

    :landing (if (config/enterprise?) enterprise-landing/home landing/home)
    :documentation docs/documentation
    :pricing pricing/pricing

    :signup signup/signup

    :error errors/error-page))

(defn app* [app owner {:keys [reinstall-om!]}]
  (reify
    om/IDisplayName (display-name [_] "App")
    om/IWillMount
    (will-mount [_]
      (let [logged-in? (boolean (get-in app state/user-path))
            api-ch (get-in app [:comms :api])]
        (if (and logged-in? (feature/enabled? :ui-fp-top-bar))
          (api/get-orgs api-ch))))
    om/IRender
    (render [_]
      (if-not (:navigation-point app)
        (html [:div#app])

        (let [persist-state! #(raise! owner [:state-persisted])
              restore-state! #(do (raise! owner [:state-restored])
                                  ;; Components are not aware of external state changes.
                                  (reinstall-om!))
              show-inspector? (get-in app state/show-inspector-path)
              logged-in? (get-in app state/user-path)
              ;; simple optimzation for real-time updates when the build is running
              app-without-container-data (dissoc-in app state/container-data-path)
              dom-com (dominant-component app owner)
              show-footer? (not= :signup (:navigation-point app))
              project (get-in app state/project-path)]
          (reset! keymap {["ctrl+s"] persist-state!
                          ["ctrl+r"] restore-state!})
          (html
           (let [inner? (get-in app state/inner?-path)]

             [:div#app {:class (concat [(if inner? "inner" "outer")]
                                       (when-not logged-in? ["aside-nil"])
                                       ;; The following is meant for the landing ab test to hide old header/footer
                                       (when (= :pricing (:navigation-point app)) ["pricing"]))}
              (om/build keyq/KeyboardHandler app-without-container-data
                        {:opts {:keymap keymap
                                :error-ch (get-in app [:comms :errors])}})
              (when (and inner? logged-in?)
                (om/build aside/aside-nav (dissoc app-without-container-data :current-build-data)))

              [:main.app-main.new-app-main-margin {:ref "app-main"}
               (when (and inner? logged-in? (feature/enabled? :ui-fp-top-bar))
                 (om/build top-nav/top-nav app-without-container-data))

               (when show-inspector?
                 ;; TODO inspector still needs lots of work. It's slow and it defaults to
                 ;;     expanding all datastructures.
                 (om/build inspector/inspector app))

               (om/build header/header app-without-container-data)

               [:div.app-dominant
                (when (and inner? logged-in?)
                  (om/build aside/aside (dissoc app-without-container-data :current-build-data)))


                [:div.main-body
                 (om/build dom-com app)

                 (when (and (not inner?) show-footer? (config/footer-enabled?))
                   [:footer.main-foot
                    (footer/footer)])]]]])))))))


(defn app [app owner opts]
  (reify
    om/IDisplayName (display-name [_] "App Wrapper")
    om/IRender (render [_] (om/build app* (dissoc app :inputs :state-map) {:opts opts}))))
