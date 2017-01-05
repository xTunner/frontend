(ns frontend.components.dashboard
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.api :as api]
            [frontend.async :refer [raise!]]
            [frontend.components.builds-table :as builds-table]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.components.project.common :as project-common]
            [frontend.models.feature :as feature]
            [frontend.models.plan :as plan-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn build-empty-state [data owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (api/get-vcs-activity (om/get-shared owner [:comms :api]))
      ((om/get-shared owner :track-event) {:event-type :nux-bootstrap-impression}))
    om/IRender
    (render [_]
      (let [building-projects (:building-projects data)
            not-building-projects (:not-building-projects data)]
        (html
          [:div.no-projects-block
           (if-not (or building-projects not-building-projects)
             [:div.spinner (spinner)]
             (let [avatar-url (get-in data [:current-user :identities :github :avatar_url])
                   cta-button-text (if (not-empty not-building-projects) "Follow and Build" "Follow")
                   event-properties (let [selected-building-projects-count (->> building-projects
                                                                                (filter :checked)
                                                                                count)
                                          selected-not-building-projects-count (->> not-building-projects
                                                                                    (filter :checked)
                                                                                    count)]
                                      {:button-text cta-button-text
                                       :selected-building-projects-count selected-building-projects-count
                                       :selected-not-building-projects-count selected-not-building-projects-count
                                       :displayed-building-projects-count (count building-projects)
                                       :displayed-not-building-projects-count (count not-building-projects)
                                       :total-displayed-projects-count (+ (count building-projects)
                                                                          (count not-building-projects))
                                       :total-selected-projects-count (+ selected-building-projects-count
                                                                         selected-not-building-projects-count)})
                   project-checkboxes (fn [projects path]
                                        (->> projects
                                             (map-indexed
                                               (fn [index project]
                                                 [:div.checkbox
                                                  [:label
                                                   [:input {:type "checkbox"
                                                            :checked (:checked project)
                                                            :name "follow checkbox"
                                                            :on-click #(utils/toggle-input owner (conj path
                                                                                                       index
                                                                                                       :checked)
                                                                                           %)}]
                                                   (str (:username project) " / " (:reponame project))]]))))
                   deselect-activity-repos (fn [path]
                                             ((om/get-shared owner :track-event) {:event-type :deselect-all-projects-clicked
                                                                                  :properties event-properties})
                                             (raise! owner [:deselect-activity-repos {:path path}]))]
               (card/collection
                 [(card/basic
                    (empty-state/empty-state
                      {:icon (empty-state/avatar-icons
                               [(gh-utils/make-avatar-url {:avatar_url avatar-url} :size 60)])
                       :heading (html [:span (empty-state/important "Welcome to CircleCI!")])
                       :subheading (html
                                     [:div
                                      [:div "Build and follow projects to populate your dashboard and receive build status emails."]
                                      [:div "To get started, here are the projects that you’ve committed to recently."]])}))
                  (when (or (not-empty building-projects)
                            (not-empty not-building-projects))
                    (card/titled
                      {:title "Getting Started"}
                      (html
                        [:div.getting-started
                         (when (not-empty building-projects)
                           [:div
                            [:h2.no-top-padding "Follow projects"]
                            [:div "These projects are already building on CircleCI. Would you like to follow them?"]
                            (project-checkboxes building-projects (state/vcs-recent-active-projects-path true :github))
                            [:a {:on-click #(deselect-activity-repos (state/vcs-recent-active-projects-path true :github))}
                             "Deselect all projects"]
                            (when (not-empty not-building-projects)
                              [:hr])])
                         (when (not-empty not-building-projects)
                           [:div
                            [:h2 "Build projects"]
                            [:div "These are your projects that are not building on CircleCI yet. Would you like to start building and following these?"]
                            (project-checkboxes not-building-projects (state/vcs-recent-active-projects-path false :github))
                            [:a {:on-click #(deselect-activity-repos (state/vcs-recent-active-projects-path false :github))}
                             "Deselect all projects"]])

                         (button/managed-button {:kind :primary
                                                 :loading-text "Following..."
                                                 :failed-text "Failed"
                                                 :success-text "Success!"
                                                 :disabled? (->> (concat building-projects not-building-projects)
                                                                 (some :checked)
                                                                 not)
                                                 :on-click #(do
                                                              ((om/get-shared owner :track-event) {:event-type :follow-and-build-projects-clicked
                                                                                                   :properties event-properties})
                                                              (raise! owner [:followed-projects]))}
                                                cta-button-text)])))
                  (card/titled
                    {:title "Don't see a project?"}
                    (html
                      [:div
                       "Visit the "
                       [:a {:href "/add-projects"} "Add Projects"]
                       " page to find it."]))])))])))))

(defn dashboard [data owner]
  (reify
    om/IDisplayName (display-name [_] "Dashboard")
    om/IRender
    (render [_]
      (let [builds (get-in data state/recent-builds-path)
            workflow (get-in data state/workflow-path)
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
                 :dashboard (om/build build-empty-state {:building-projects (get-in data (state/vcs-recent-active-projects-path true :github))
                                                         :not-building-projects (get-in data (state/vcs-recent-active-projects-path false :github))
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
                           :projects projects
                           :workflow workflow}
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
