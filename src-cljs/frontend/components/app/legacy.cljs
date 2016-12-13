(ns frontend.components.app.legacy
  (:require [frontend.components.account :as account]
            [frontend.components.admin :as admin]
            [frontend.components.dashboard :as dashboard]
            [frontend.components.enterprise-landing :as enterprise-landing]
            [frontend.components.errors :as errors]
            [frontend.components.insights :as insights]
            [frontend.components.landing :as landing]
            [frontend.components.org-settings :as org-settings]
            [frontend.components.pages.add-projects :as add-projects]
            [frontend.components.pages.build :as build]
            [frontend.components.pages.project-insights :as project-insights]
            [frontend.components.pages.project-settings :as project-settings]
            [frontend.components.pages.team :as team]
            [frontend.components.templates.main :as main-template]
            [frontend.config :as config]
            [frontend.utils.legacy :refer [build-legacy]]
            [om.core :as om :include-macros true]
            [om.next :as om-next :refer-macros [defui]]))

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
   {:add-projects add-projects/page
    :build build/page
    :project-insights project-insights/page
    :project-settings project-settings/page
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

          :landing (fn [app owner]
                     (reify
                       om/IRender
                       (render [_]
                         (om/build
                          (if (config/enterprise?) enterprise-landing/home landing/home)
                          app))))

          :error errors/error-page})))

(defui ^:once LegacyPage
  static om-next/IQuery
  (query [this]
    '[{:legacy/state [*]}])
  Object
  (render [this]
    (let [app (:legacy/state (om-next/props this))
          page (get nav-point->page (:navigation-point app))]
      (when page
        (build-legacy page app)))))
