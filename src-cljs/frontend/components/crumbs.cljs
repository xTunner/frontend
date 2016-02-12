(ns frontend.components.crumbs
  (:require [frontend.routes :as routes]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om])
  (:require-macros [frontend.utils :refer [html]]))

(defn crumb-node [{:keys [active name path]}]
  (if active
    [:li.active
     [:a {:disabled true :title name} name " "]]
    [:li
     [:a {:href path :title name} name " "]]))

(defmulti render-crumb
  (fn [{:keys [type]}] type))

(defmethod render-crumb :default
  [attrs]
  (crumb-node attrs))

(defmethod render-crumb :dashboard
  [attrs]
  (crumb-node {:name "Builds"
               :path (routes/v1-dashboard-path {})}))

(defmethod render-crumb :project
  [{:keys [username project active]}]
  (crumb-node {:name project
               :path (routes/v1-dashboard-path {:org username :repo project})
               :active active}))

(defmethod render-crumb :project-settings
  [{:keys [username project active]}]
  (crumb-node {:name "project settings"
               :path (routes/v1-project-settings {:org username :repo project})
               :active active}))

(defmethod render-crumb :project-branch
  [{:keys [username project branch active]}]
  (crumb-node {:name (if branch
                       (utils/trim-middle (utils/display-branch branch) 45)
                       "...")
               :path (routes/v1-dashboard-path {:org username
                                                :repo project
                                                :branch branch})
               :active active}))

(defmethod render-crumb :build
  [{:keys [username project build-num active]}]
  (crumb-node {:name (str "build " build-num)
               :path (routes/v1-build-path username project build-num)
               :active active}))

(defmethod render-crumb :org
  [{:keys [username active]}]
  (crumb-node {:name username
               :path (routes/v1-dashboard-path {:org username})
               :active active}))

(defmethod render-crumb :org-settings
  [{:keys [username active]}]
  (crumb-node {:name "organization settings"
               :path (routes/v1-org-settings {:org username})
               :active active}))

(defmethod render-crumb :add-projects
  [attrs]
  (crumb-node {:name "Add Projects"
               :path (routes/v1-add-projects)}))

(defmethod render-crumb :invite-teammates
  [attrs]
  (crumb-node {:name "Invite Teammates"
               :path (routes/v1-invite-teammates)}))

(defmethod render-crumb :account
  [attrs]
  (crumb-node {:name "Account"
               :path (routes/v1-account)}))

(defmethod render-crumb :settings-base
  [attrs]
  (crumb-node {:name "Settings"
               :active false}))

(defmethod render-crumb :build-insights
  [attrs]
  (crumb-node {:name "Insights"
               :path (routes/v1-insights)
               :active false}))

(defmethod render-crumb :insights-repositories
  [attrs]
  (crumb-node {:name "Repositories"
               :active false}))

(defmethod render-crumb :insights-branch
  [{:keys [branch]}]
  (crumb-node {:name branch
               :active false}))

(defn crumbs [crumbs-data]
  (map render-crumb crumbs-data))
