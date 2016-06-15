(ns frontend.components.app
  (:require [frontend.api :as api]
            [frontend.async :refer [raise!]]
            [frontend.components.account :as account]
            [frontend.components.add-projects :as add-projects]
            [frontend.components.admin :as admin]
            [frontend.components.aside :as aside]
            [frontend.components.dashboard :as dashboard]
            [frontend.components.enterprise-landing :as enterprise-landing]
            [frontend.components.errors :as errors]
            [frontend.components.insights :as insights]
            [frontend.components.insights.project :as project-insights]
            [frontend.components.invites :as invites]
            [frontend.components.key-queue :as keyq]
            [frontend.components.landing :as landing]
            [frontend.components.org-settings :as org-settings]
            [frontend.components.pages.build :as build]
            [frontend.components.pages.projects :as projects]
            [frontend.components.project-settings :as project-settings]
            [frontend.components.templates.main :as main-template]
            [frontend.config :as config]
            [frontend.models.feature :as feature]
            [frontend.state :as state]
            [frontend.utils.seq :refer [dissoc-in]]
            [goog.dom :as gdom]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(def keymap
  (atom nil))

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
   ;; Page components, which are good as they are.
   {:build build/page}
   ;; Old-World dominant components which need to be wrapped in the `main` template. As we
   ;; migrate these, we'll move them into the map above.
   (into {}
         (map #(vector (key %) (templated (val %))))
         {:dashboard dashboard/dashboard
          :add-projects add-projects/add-projects
          :build-insights insights/build-insights
          :project-insights project-insights/project-insights
          :invite-teammates invites/teammates-invites
          :project-settings project-settings/project-settings
          :org-settings org-settings/org-settings
          :account account/account
          :projects projects/page

          :admin-settings admin/admin-settings
          :build-state admin/build-state
          :switch admin/switch

          :landing (if (config/enterprise?) enterprise-landing/home landing/home)

          :error errors/error-page})))

(defn app* [app owner {:keys [reinstall-om!]}]
  (reify
    om/IDisplayName (display-name [_] "App")
    om/IWillMount
    (will-mount [_]
      (let [logged-in? (boolean (get-in app state/user-path))
            api-ch (get-in app [:comms :api])]
        (if (and logged-in? (feature/enabled? :ui-fp-top-bar))
          (api/get-orgs api-ch :include-user? true))))
    om/IRender
    (render [_]
      (if-not (:navigation-point app)
        (html [:div#app])

        (let [persist-state! #(raise! owner [:state-persisted])
              restore-state! #(do (raise! owner [:state-restored])
                                  ;; Components are not aware of external state changes.
                                  (reinstall-om!))
              logged-in? (get-in app state/user-path)
              ;; simple optimzation for real-time updates when the build is running
              app-without-container-data (dissoc-in app state/container-data-path)
              page (nav-point->page (:navigation-point app))]
          (reset! keymap {["ctrl+s"] persist-state!
                          ["ctrl+r"] restore-state!})
          (html
           (let [inner? (get-in app state/inner?-path)]

             [:div#app {:class (concat [(if inner? "inner" "outer")]
                                       ;; The following is meant for the landing ab test to hide old header/footer
                                       (when (= :pricing (:navigation-point app)) ["pricing"]))
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
              (om/build keyq/KeyboardHandler app-without-container-data
                        {:opts {:keymap keymap
                                :error-ch (get-in app [:comms :errors])}})
              (when (and inner? logged-in?)
                (om/build aside/aside-nav (dissoc app-without-container-data :current-build-data)))

              (om/build page app)])))))))


(defn app [app owner opts]
  (reify
    om/IDisplayName (display-name [_] "App Wrapper")
    om/IRender (render [_] (om/build app* (dissoc app :inputs :state-map) {:opts opts}))))
