(ns frontend.components.dashboard
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.analytics :as analytics]
            [frontend.async :refer [raise!]]
            [frontend.components.builds-table :as builds-table]
            [frontend.components.common :as common]
            [frontend.components.project.common :as project-common]
            [frontend.models.plan :as plan-model]
            [frontend.models.project :as project-model]
            [frontend.models.feature :as feature]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn build-diagnostics [{:keys [data project]} owner]
  (reify
    om/IDisplayName (display-name [_] "Build Diagnostics")
    om/IRender
    (render [_]
      (let [diagnostics (get-in data state/project-build-diagnostics-path)
            project-id-hash (utils/md5 (project-model/id project))
            collapsed? (get-in data (state/project-build-diagnostics-collapsed-path project-id-hash))
            repo-name (project-model/repo-name project)
            org-name (project-model/org-name project)]
        (if (empty? diagnostics)
          nil
          (html
           [:.build-diagnostics
            {:class (when collapsed? "collapsed")}
            [:.diagnostics-header
             {:on-click #(raise! owner [:collapse-build-diagnostics-toggled {:project-id-hash project-id-hash}])}
             [:.icon
              [:i.fa.fa-tachometer]]
             [:.body
              [:span.title "Build Diagnostics"]
              "Tired of waiting for your builds to start?"]
             [:div
              [:i.fa.fa-chevron-down]]]
            [:ul.diagnostics
             (for [diagnostic diagnostics
                   ;; When we have more than one type of diagnostic, we should
                   ;; switch on :type, perhaps with a multimethod. For now, we
                   ;; filter for the only :type we know of.
                   :when (= "long-usage-queue" (:type diagnostic))]
               [:li
                [:.icon
                 [:i.fa.fa-clock-o]]
                [:.body
                 [:b.repo-name (project-model/project-name project)]
                 " could be faster because its average queue time is "
                 [:b.time (-> diagnostic
                              :avg_usage_queue_wait_ms
                              (/ 60000)
                              Math/floor) " minutes"]
                 "."]
                [:div
                 [:a {:href (routes/v1-org-settings-subpage {:org (:plan_org_name diagnostic)
                                                             :subpage "containers"})
                      :on-click #(analytics/track {:event-type :add-more-containers-clicked
                                                   :owner owner
                                                   :properties {:org org-name
                                                                :repo repo-name}})}
                  "Add More Containers"]]])]]))))))

(defn dashboard [data owner]
  (reify
    om/IDisplayName (display-name [_] "Dashboard")
    om/IRender
    (render [_]
      (let [builds (:recent-builds data)
            projects (get-in data state/projects-path)
            current-project (get-in data state/project-data-path)
            plan (:plan current-project)
            project (:project current-project)
            nav-data (:navigation-data data)
            page (js/parseInt (get-in nav-data [:query-params :page] 0))
            builds-per-page (:builds-per-page data)]
        (html
         (cond (nil? builds)
               [:div.loading-spinner-big common/spinner]

               (and (empty? builds)
                    projects
                    (empty? projects))
               [:div
                [:h2 "You don't have any projects in CircleCI!"]
                [:p "Why don't you add a repository or two on the "
                 [:a {:href (routes/v1-add-projects)} "Manage Projects page"] "?"]]

               :else
               [:div.dashboard
                (when (and (feature/enabled? :build-diagnostics)
                           project)
                  (om/build build-diagnostics {:data data :project project}))
                (when (and plan (project-common/show-trial-notice? project plan))
                  [:div.container-fluid
                   [:div.row
                    [:div.col-xs-12
                     (om/build project-common/trial-notice current-project)]]])

                (when (plan-model/suspended? plan)
                  (om/build project-common/suspended-notice plan))

                (om/build builds-table/builds-table builds {:opts {:show-actions? true
                                                                   :show-branch? (not (:branch nav-data))
                                                                   :show-project? (not (:repo nav-data))}})
                [:div.recent-builds-pager
                 [:a
                  {:href (routes/v1-dashboard-path (assoc nav-data :page (max 0 (dec page))))
                   ;; no newer builds if you're on the first page
                   :class (when (zero? page) "disabled")}
                  [:i.fa.fa-long-arrow-left]
                  [:span " Newer builds"]]
                 [:a
                  {:href (routes/v1-dashboard-path (assoc nav-data :page (inc page)))
                   ;; no older builds if you have less builds on the page than an
                   ;; API call returns
                   :class (when (> builds-per-page (count builds)) "disabled")}
                  [:span "Older builds "]
                  [:i.fa.fa-long-arrow-right]]]]))))))
