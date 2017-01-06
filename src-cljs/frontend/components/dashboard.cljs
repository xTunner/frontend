(ns frontend.components.dashboard
  (:require [frontend.async :refer [raise!]]
            [frontend.components.builds-table :as builds-table]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.components.project.common :as project-common]
            [frontend.experiments.nux-bootstrap :as nux-bootstrap]
            [frontend.models.feature :as feature]
            [frontend.models.plan :as plan-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn dashboard [data owner]
  (reify
    om/IDisplayName (display-name [_] "Dashboard")
    om/IRender
    (render [_]
      (let [builds (get-in data state/recent-builds-path)
            projects (get-in data state/projects-path)
            current-project (get-in data state/project-data-path)
            plan (:plan current-project)
            project (:project current-project)
            nav-data (:navigation-data data)
            page (js/parseInt (get-in nav-data [:query-params :page] 0))
            builds-per-page (:builds-per-page data)
            current-user (:current-user data)]
        (html
          ;; ensure the both projects and builds are loaded before rendering to prevent
          ;; the build list and branch picker from resizing.
          (cond (or (nil? builds)
                    ;; if the user isn't logged in, but is viewing an oss build,
                    ;; then we will not load any projects.
                    (and current-user (nil? projects)))
                [:div.empty-placeholder (spinner)]

               (and (empty? builds)
                    projects
                    (empty? projects))
               (case (feature/ab-test-treatment :new-user-landing-page current-user)
                 :dashboard (om/build nux-bootstrap/build-empty-state
                                      {:building-projects (-> (get-in data (state/vcs-recent-active-projects-path true :github))
                                                              vals)
                                       :not-building-projects (-> (get-in data (state/vcs-recent-active-projects-path false :github))
                                                                  vals)
                                       :projects-loaded? (get-in data state/vcs-activity-loaded-path)
                                       :current-user current-user})
                 :add-projects [:div
                                [:h2 "You don't have any projects in CircleCI!"]
                                [:p "Why don't you add a project or two on the "
                                 [:a {:href (routes/v1-add-projects-path {})} "Manage Projects page"] "?"]])
               :else
               [:div.dashboard
                (when (project-common/show-trial-notice? project plan (get-in data state/dismissed-trial-update-banner))
                  [:div.container-fluid
                   [:div.row
                    [:div.col-xs-12
                     (om/build project-common/trial-notice current-project)]]])

                (when (plan-model/suspended? plan)
                  (om/build project-common/suspended-notice {:plan plan
                                                             :vcs_type (:vcs_type project)}))

                (om/build builds-table/builds-table
                          {:builds builds
                           :projects projects}
                          {:opts {:show-actions? true
                                  :show-branch? (not (:branch nav-data))
                                  :show-project? (not (:repo nav-data))}})
                [:div.recent-builds-pager
                 [:a
                  {:href (routes/v1-dashboard-path (assoc nav-data :page (max 0 (dec page))))
                   ;; no newer builds if you're on the first page
                   :class (when (zero? page) "disabled")}
                  [:i.fa.fa-long-arrow-left.newer]
                  [:span "Newer builds"]]
                 [:a
                  {:href (routes/v1-dashboard-path (assoc nav-data :page (inc page)))
                   ;; no older builds if you have less builds on the page than an
                   ;; API call returns
                   :class (when (> builds-per-page (count builds)) "disabled")}
                  [:span "Older builds"]
                  [:i.fa.fa-long-arrow-right.older]]]]))))))
