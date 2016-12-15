(ns frontend.components.pieces.page-header
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.pieces.button :as button]
            [frontend.routes :as routes]
            [frontend.utils :as utils]
            [frontend.utils.devcards :refer [iframe]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component html]]))

(defn crumb-node [{:keys [active name path track-event-type]} owner]
  (reify
    om/IRender
    (render [_]
      (component
        (html 
          (if active
            [:li.active
             [:a {:disabled true :title name} name " "]]
            [:li
             [:a {:href path
                  :title name
                  :on-click (when track-event-type
                              #((om/get-shared owner :track-event) {:event-type track-event-type}))}
                 name " "]]))))))

(defmulti crumb
  (fn [{:keys [type]}] type))

(defmethod crumb :default
  [attrs]
  (om/build crumb-node attrs))

(defmethod crumb :dashboard
  [{:keys [owner]}]
  (om/build crumb-node {:name "Builds"
                        :path (routes/v1-dashboard-path {})
                        :track-event-type :breadcrumb-dashboard-clicked}))

(defmethod crumb :project
  [{:keys [vcs_type username project active owner]}]
  (om/build crumb-node {:name project
                        :path (routes/v1-dashboard-path {:vcs_type vcs_type :org username :repo project})
                        :active active
                        :track-event-type :breadcrumb-project-clicked}))

(defmethod crumb :project-settings
  [{:keys [vcs_type username project active]}]
  (om/build crumb-node {:name "project settings"
                        :path (routes/v1-project-settings-path {:vcs_type vcs_type :org username :repo project})
                        :active active}))

(defmethod crumb :project-branch
  [{:keys [vcs_type username project branch active tag owner]}]
  (om/build crumb-node {:name (cond
                                tag (utils/trim-middle (utils/display-tag tag) 45)
                                branch (utils/trim-middle (utils/display-branch branch) 45)
                                :else "...")
                       :path (when branch
                               (routes/v1-dashboard-path {:vcs_type vcs_type
                                                          :org username
                                                          :repo project
                                                          :branch branch}))
                       :track-event-type :breadcrumb-branch-clicked
                       :active active}))

(defmethod crumb :build
  [{:keys [vcs_type username project build-num active]}]
  (om/build crumb-node {:name (str "build " build-num)
                        :track-event-type :breadcrumb-build-clicked
                        :path (routes/v1-build-path vcs_type username project build-num)
                        :active active}))

(defmethod crumb :org
  [{:keys [vcs_type username active owner]}]
  (om/build crumb-node {:name username
                        :track-event-type :breadcrumb-org-clicked
                        :path (routes/v1-dashboard-path {:vcs_type vcs_type
                                                         :org username})
                        :active active}))

(defmethod crumb :org-settings
  [{:keys [vcs_type username active]}]
  (om/build crumb-node {:name "organization settings"
                        :path (routes/v1-org-settings-path {:org username
                                                            :vcs_type vcs_type})
                        :active active}))

(defmethod crumb :add-projects
  [attrs]
  (om/build crumb-node {:name "Add Projects"
                        :path (routes/v1-add-projects)}))

(defmethod crumb :projects
  [attrs]
  (om/build crumb-node {:name "Projects"
                        :path (routes/v1-projects)}))

(defmethod crumb :team
  [attrs]
  (om/build crumb-node {:name "Team"
                        :path (routes/v1-team)}))

(defmethod crumb :account
  [attrs]
  (om/build crumb-node {:name "Account"
                        :path (routes/v1-account)}))

(defmethod crumb :settings-base
  [attrs]
  (om/build crumb-node {:name "Settings"
                        :active false}))

(defmethod crumb :build-insights
  [attrs]
  (om/build crumb-node {:name "Insights"
                        :path (routes/v1-insights)
                        :active false}))

(defn header
  "The page header.

  :crumbs  - The breadcrumbs to display.
  :actions - (optional) A component (or collection of components) which will be
             placed on the right of the header. This is where page-wide actions are
             placed."
  [{:keys [crumbs actions]} owner]
  (reify
    om/IDisplayName (display-name [_] "User Header")
    om/IRender
    (render [_]
      (component
        (html
         [:div
          [:ol.breadcrumbs (map crumb crumbs)]
          [:.actions actions]])))))

(dc/do
  (def ^:private crumbs
    [{:type :dashboard}
     {:type :org
      :username "some-org"
      :vcs_type "github"}
     {:type :project
      :username "some-org"
      :project "a-project"
      :vcs_type "github"}
     {:type :project-branch
      :username "some-org"
      :project "a-project"
      :vcs_type "github"
      :branch "a-particular-branch"}
     {:type :build
      :username "some-org"
      :project "a-project"
      :build-num 66908
      :vcs_type "github"}])

  (defcard header-with-no-actions
    (iframe
     {:width "992px"}
     (om/build header {:crumbs crumbs})))

  (defcard header-with-no-actions-narrow
    (iframe
     {:width "991px"}
     (om/build header {:crumbs crumbs})))

  (defcard header-with-actions
    (iframe
     {:width "992px"}
     (om/build header {:crumbs crumbs
                       :actions [(button/button {} "Do Something")
                                 (button/button {:kind :primary} "Do Something")]})))

  (defcard header-with-actions-narrow
    (iframe
     {:width "991px"}
     (om/build header {:crumbs crumbs
                       :actions [(button/button {} "Do Something")
                                 (button/button {:kind :primary} "Do Something")]}))))
