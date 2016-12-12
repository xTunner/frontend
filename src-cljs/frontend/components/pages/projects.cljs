(ns frontend.components.pages.projects
  (:require [frontend.analytics :as analytics]
            [frontend.async :refer [navigate!]]
            [frontend.components.app.legacy :as legacy]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.org-picker :as org-picker]
            [frontend.components.pieces.table :as table]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.components.templates.main :as main-template]
            [frontend.models.project :as project-model]
            [frontend.models.user :as user]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :refer [set-page-title!]]
            [frontend.utils.function-query :as fq :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.legacy :refer [build-legacy]]
            [frontend.utils.vcs :as vcs]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [frontend.utils :refer [component element html]]))

(defn- table
  {::fq/queries {:projects (fq/merge [:project/vcs-url
                                      :project/name]
                                     (fq/get project-model/parallelism :project)
                                     (fq/get project-model/buildable-parallelism :project))
                 :plan (fq/get project-model/buildable-parallelism :plan)}}
  [c projects plan]
  (component
    (build-legacy table/table
                  {:rows projects
                   :key-fn :project/vcs-url
                   :columns [{:header "Project"
                              :cell-fn
                              (fn [project]
                                (html
                                 (let [vcs-url (:project/vcs-url project)]
                                   [:a
                                    {:href (routes/v1-project-dashboard-path {:vcs_type (vcs-url/vcs-type vcs-url)
                                                                              :org (vcs-url/org-name vcs-url)
                                                                              :repo (vcs-url/repo-name vcs-url)})
                                     :on-click #(analytics/track! c {:event-type :project-clicked
                                                                     :properties {:vcs-type (vcs-url/vcs-type vcs-url)
                                                                                  :org (vcs-url/org-name vcs-url)
                                                                                  :repo (vcs-url/repo-name vcs-url)}})}
                                    (:project/name project)])))}

                             {:header "Parallelism"
                              :type #{:right :shrink}
                              :cell-fn
                              #(html
                                (let [parallelism (project-model/parallelism %)
                                      buildable-parallelism (when plan (project-model/buildable-parallelism plan %))
                                      vcs-url (:project/vcs-url %)]
                                  [:a {:href (routes/v1-project-settings-path {:vcs_type (-> vcs-url vcs-url/vcs-type vcs/->short-vcs)
                                                                               :org (vcs-url/org-name vcs-url)
                                                                               :repo (vcs-url/repo-name vcs-url)
                                                                               :_fragment "parallel-builds"})}
                                   parallelism "x"
                                   (when buildable-parallelism (str " out of " buildable-parallelism "x"))]))}

                             {:header "Team"
                              :type #{:right :shrink}
                              :cell-fn #(count (:project/followers %))}

                             {:header "Settings"
                              :type :shrink
                              :cell-fn
                              #(table/action-link
                                "Settings"
                                (icon/settings)
                                (let [vcs-url (:project/vcs-url %)]
                                  (routes/v1-project-settings-path {:vcs_type (vcs-url/vcs-type vcs-url)
                                                                    :org (vcs-url/org-name vcs-url)
                                                                    :repo (vcs-url/repo-name vcs-url)})))}]})))

(defn- no-org-selected [available-orgs bitbucket-enabled?]
  (component
    (card/basic
     (empty-state/empty-state {:icon (if-let [orgs (seq (take 3 available-orgs))]
                                       (empty-state/avatar-icons
                                        (for [{:keys [organization/avatar-url]} orgs]
                                          (gh-utils/make-avatar-url {:avatar_url avatar-url} :size 60)))
                                       (icon/team))
                               :heading (html
                                         [:span
                                          "Get started by selecting your "
                                          (empty-state/important "organization")])
                               :subheading (str
                                            "Select your GitHub "
                                            (when bitbucket-enabled? "or Bitbucket ")
                                            "organization (or username) to view your projects.")}))))

(defui ^:once AddProjectButton
  Object
  (render [this]
    (let [{:keys [empty-state?]} (om-next/props this)]
      (button/link
       {:href (routes/v1-add-projects)
        :kind :primary
        :size :medium
        :on-click #(analytics/track! this {:event-type :add-project-clicked
                                           :properties {:is-empty-state empty-state?}})}
       "Add Project"))))

(def add-project-button (om-next/factory AddProjectButton))

(defn- no-projects-available [org-name]
  (empty-state/empty-state {:icon (icon/project)
                            :heading (html
                                      [:span
                                       (empty-state/important org-name)
                                       " has no projects building on CircleCI"])
                            :subheading "Let's fix that by adding a new project."
                            :action (add-project-button {:empty-state? true})}))


(defui ^:once OrgProjects
  static om-next/Ident
  (ident [this {:keys [organization/vcs-type organization/name]}]
    [:organization/by-vcs-type-and-name {:organization/vcs-type vcs-type :organization/name name}])
  static om-next/IQuery
  (query [this]
    [:organization/vcs-type
     :organization/name
     {:organization/projects (fq/merge [{:project/followers []}]
                                       (fq/get table :projects))}
     {:organization/plan (fq/get table :plan)}])
  Object
  (render [this]
    (component
      (let [{:keys [organization/vcs-type organization/name organization/projects organization/plan]} (om-next/props this)
            vcs-icon (case vcs-type
                       "github" (icon/github)
                       "bitbucket" (icon/bitbucket)
                       nil)]
        (card/titled {:title (element :title
                               (html
                                [:div
                                 [:.vcs-icon vcs-icon]
                                 name]))}
                     (if projects
                       (if-let [projects-with-followers
                                (seq (filter #(seq (:project/followers %)) projects))]
                         (table this projects-with-followers plan)
                         (no-projects-available name))
                       (spinner)))))))

(def org-projects (om-next/factory OrgProjects))

(defui ^:once Page
  static om-next/IQuery
  (query [this]
    ;; NB: Every Page *must* query for {:legacy/state [*]}, to make it available
    ;; to frontend.components.app/Wrapper. This is necessary until the compassus
    ;; root query can be customized. See
    ;; https://github.com/compassus/compassus/issues/3
    ['{:legacy/state [*]}
     {:app/current-user [{:user/organizations (om-next/get-query org-picker/Organization)}
                         :user/login
                         :user/bitbucket-authorized?]}
     {:app/route-data [{:route-data/organization (into (om-next/get-query OrgProjects)
                                                       [:organization/name])}]}])
  analytics/Properties
  (properties [this]
    (let [props (om-next/props this)]
      {:user (get-in props [:app/current-user :user/login])
       :view :projects
       :org (get-in props [:app/route-data :route-data/organization :organization/name])}))
  Object
  (componentDidMount [this]
    (set-page-title! "Projects"))
  (render [this]
    (component
      (build-legacy
       main-template/template
       {:app (:legacy/state (om-next/props this))
        :crumbs [{:type :projects}]
        :header-actions (add-project-button {:empty-state? false})
        :show-aside-menu? false
        :main-content
        (element :main-content
          (let [current-user (:app/current-user (om-next/props this))
                orgs (get-in (om-next/props this) [:app/current-user :user/organizations])
                selected-org (get-in (om-next/props this) [:app/route-data :route-data/organization])]
            (html
             [:div
              [:.sidebar
               (card/basic
                (if orgs
                  (org-picker/picker
                   {:orgs orgs
                    :selected-org (first (filter #(= (select-keys selected-org [:organization/vcs-type :organization/name])
                                                     (select-keys % [:organization/vcs-type :organization/name]))
                                                 orgs))
                    :on-org-click (fn [{:keys [organization/vcs-type organization/name]}]
                                    (analytics/track! this {:event-type :org-clicked
                                                            :properties {:login name
                                                                         :vcs_type vcs-type}})
                                    (navigate! this (routes/v1-organization-projects-path {:org name :vcs_type vcs-type})))})
                  (spinner)))]
              [:.main
               (if selected-org
                 (org-projects selected-org)
                 (no-org-selected orgs (:user/bitbucket-authorized? current-user)))]])))}))))
