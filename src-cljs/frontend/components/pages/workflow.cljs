(ns frontend.components.pages.workflow
  (:require [clojure.string :as string]
            [frontend.api :as api]
            [frontend.components.aside :as aside]
            [frontend.components.common :as common]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.components.templates.main :as main-template]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.models.feature :as feature]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :refer [set-page-title!] :refer-macros [component element html]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.legacy :refer [build-legacy]]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om]
            [om.next :as om-next :refer-macros [defui]]))

(defn- status-class [run-status]
  (case run-status
    :run-status/running :status-class/running
    :run-status/succeeded :status-class/succeeded
    :run-status/failed :status-class/failed
    (:run-status/canceled :run-status/not-run) :status-class/stopped))

(def ^:private cancelable-statuses #{:run-status/not-run
                                     :run-status/running})

(def ^:private rerunnable-statuses #{:run-status/succeeded
                                     :run-status/failed
                                     :run-status/canceled})

(def ^:private rerunnable-from-start-statuses #{:run-status/failed})

(defn run-prs
  "A om-next compatible version of
  `frontend.components.builds-table/pull-requests`."
  [pull-requests]
  (html
   (when-let [urls (seq (map :pull-request/url pull-requests))]
     [:span.metadata-item.pull-requests {:title "Pull Requests"}
      (icon/git-pull-request)
      (interpose
       ", "
       (for [url urls
             ;; WORKAROUND: We have/had a bug where a PR URL would be reported as nil.
             ;; When that happens, this code blows up the page. To work around that,
             ;; we just skip the PR if its URL is nil.
             :when url]
         [:a {:href url}
          "#"
          (gh-utils/pull-request-number url)]))])))

(defn- commit-link
  "Om Next compatible version of `frontend.components.builds-table/commits`."
  [vcs-type org repo sha]
  (html
   (when (and vcs-type org repo sha)
     (let [pretty-sha (build-model/github-revision {:vcs_revision sha})]
       [:span.metadata-item.revision
        [:i.octicon.octicon-git-commit]
        [:a {:title pretty-sha
             :href (build-model/commit-url {:vcs_revision sha
                                            :vcs_url (vcs-url/vcs-url vcs-type
                                                                      org
                                                                      repo)})}
         pretty-sha]]))))

(defn- transact-run-mutate [component mutation]
  (om-next/transact!

   ;; We transact on the reconciler, not the component; otherwise the
   ;; component's props render as nil for a moment. This is odd.
   ;;
   ;; It looks like the transaction drops the run from the app state.
   ;; Transacting on the component means the component immediately re-reads, so
   ;; it immediately renders nil. Moments later, the query is read from the
   ;; server again, delivering new data to the app state, and the component
   ;; renders with data again.
   ;;
   ;; When we transact on the reconciler, we simply avoid rendering the first
   ;; time, during the window when the run is missing. Of course, it shouldn't
   ;; be missing in the first place.
   ;;
   ;; tl;dr: there's a bug in here, but it's not clear what, and this works fine
   ;; for now.
   (om-next/get-reconciler component)

   ;; It's not clear why we have to manually transform-reads---Om should do that
   ;; for us if we give a simple keyword---but it doesn't appear to be doing it,
   ;; so we do it. This is another bug we're punting on.
   (om-next/transform-reads
    (om-next/get-reconciler component)
    [mutation])))

(defn- transact-run-retry
  [component run-id jobs]
  (transact-run-mutate component `(run/retry {:run/id ~run-id :run/jobs ~jobs})))

(defn- transact-run-cancel
  [component run-id]
  (transact-run-mutate component `(run/cancel {:run/id ~run-id})))

;; TODO: Move this to pieces.*, as it's used on the run page as well.
(defui ^:once RunRow
  ;; NOTE: this is commented out until bodhi handles queries for components with idents first
  ;; static om-next/Ident
  ;; (ident [this props]
  ;;   [:run/by-id (:run/id props)])
  static om-next/IQuery
  (query [this]
    [:run/id
     :run/name
     :run/status
     :run/started-at
     :run/stopped-at
     {:run/jobs [:job/id]}
     {:run/trigger-info [:trigger-info/vcs-revision
                         :trigger-info/subject
                         :trigger-info/body
                         :trigger-info/branch
                         {:trigger-info/pull-requests [:pull-request/url]}]}
     {:run/project [:project/name
                    {:project/organization [:organization/name
                                            :organization/vcs-type]}]}])
  Object
  (render [this]
    (component
      (let [{:keys [run/id
                    run/status
                    run/started-at
                    run/stopped-at
                    run/trigger-info
                    run/jobs]
             run-name :run/name
             {project-name :project/name
              {org-name :organization/name
               vcs-type :organization/vcs-type} :project/organization} :run/project}
            (om-next/props this)
            {commit-sha :trigger-info/vcs-revision
             commit-body :trigger-info/body
             commit-subject :trigger-info/subject
             pull-requests :trigger-info/pull-requests
             branch :trigger-info/branch} trigger-info
            run-status-class (status-class status)]

        (card/basic
         (element :content
           (html
            [:div
             [:.status-and-button
              [:div.status {:class (name run-status-class)}
               [:a.exception {:href (routes/v1-run-path id)}
                [:span.status-icon {:class (name run-status-class)}
                 (case (status-class status)
                   :status-class/failed (icon/status-failed)
                   :status-class/stopped (icon/status-canceled)
                   :status-class/succeeded (icon/status-passed)
                   :status-class/running (icon/status-running)
                   :status-class/waiting (icon/status-queued))]
                [:.status-string (string/replace (name status) #"-" " ")]]]
              (cond (cancelable-statuses status)
                    [:div.cancel-button {:on-click #(transact-run-cancel this id)}
                     (icon/status-canceled)
                     [:span "cancel"]]
                    (rerunnable-statuses status)
                    [:div.rebuild-button.dropdown
                     (icon/rebuild)
                     [:span "Rerun"]
                     [:i.fa.fa-chevron-down.dropdown-toggle {:data-toggle "dropdown"}]
                     [:ul.dropdown-menu.pull-right
                      (when (rerunnable-from-start-statuses status)
                        [:li
                         [:a
                          {:on-click #(transact-run-retry this id [])}
                          "Rerun failed jobs"]])
                      [:li
                       [:a
                        {:on-click #(transact-run-retry this id jobs)}
                        "Rerun from beginning"]]]])]
             [:div.run-info
              [:div.build-info-header
               [:div.contextual-identifier
                [:a {:href (routes/v1-run-path id)}
                 [:span  branch " / " run-name]]]]
              [:div.recent-commit-msg
               [:span.recent-log
                {:title (when commit-body
                          commit-body)}
                (when commit-subject
                  commit-subject)]]]
             [:div.metadata
              [:div.metadata-row.timing
               [:span.metadata-item.recent-time.start-time
                [:i.material-icons "today"]
                (if started-at
                  [:span {:title (str "Started: " (datetime/full-datetime started-at))}
                   (build-legacy common/updating-duration {:start started-at} {:opts {:formatter datetime/time-ago-abbreviated}})
                   [:span " ago"]]
                  "-")]
               [:span.metadata-item.recent-time.duration
                [:i.material-icons "timer"]
                (if stopped-at
                  [:span {:title (str "Duration: "
                                      (datetime/as-duration (- (.getTime stopped-at)
                                                               (.getTime started-at))))}
                   (build-legacy common/updating-duration {:start started-at
                                                           :stop stopped-at})]
                  "-")]]
              [:div.metadata-row.pull-revision
               (run-prs pull-requests)
               (commit-link vcs-type
                            org-name
                            project-name
                            commit-sha)]]])))))))

(def run-row (om-next/factory RunRow {:keyfn :run/id}))

(defn a-or-span [flag href & children]
  (if flag
    [:a {:href href} children]
    [:span children]))

(defui ^:once RunList
  static om-next/IQuery
  (query [this]
    [:connection/total-count
     :connection/offset
     {:connection/edges [{:edge/node (om-next/get-query RunRow)}]}])
  Object
  (render [this]
    (component
      (let [{:keys [connection/total-count connection/offset connection/edges]} (om-next/props this)
            {:keys [empty-state prev-page-href next-page-href]} (om-next/get-computed this)]
        (if-not (pos? total-count)
          empty-state
          (html
           [:div
            [:.page-info "Showing " [:span.run-numbers (inc offset) "–" (+ offset (count edges))]]
            (card/collection
             (map #(when % (run-row (:edge/node %))) edges))
            [:.list-pager
             (a-or-span (pos? offset)
                        prev-page-href
                        "← Newer workflow runs")
             (a-or-span (> total-count (+ offset (count edges)))
                        next-page-href
                        "Older workflow runs →")]]))))))

(def run-list (om-next/factory RunList))

(defui ^:once ProjectWorkflowRuns
  static om-next/IQuery
  (query [this]
    [:project/name
     {:project/organization [:organization/vcs-type :organization/name]}
     `{(:routed/page {:page/connection :project/workflow-runs
                      :page/count 30})
       ~(om-next/get-query RunList)}
     {'[:app/route-params _] [:page/number]}])
  Object
  (render [this]
    (component
      (let [props (om-next/props this)
            page-num (get-in props ['[:app/route-params _] :page/number])
            {project-name :project/name
             {vcs-type :organization/vcs-type
              org-name :organization/name} :project/organization} props]
        (run-list (om-next/computed
                   (:routed/page props)
                   {:prev-page-href
                    (routes/v1-project-workflows-path vcs-type org-name project-name (dec page-num))

                    :next-page-href
                    (routes/v1-project-workflows-path vcs-type org-name project-name (inc page-num))

                    :empty-state
                    (card/basic
                     (empty-state/empty-state
                      {:icon (icon/workflows)
                       :heading (html [:span (empty-state/important (str org-name "/" project-name)) " has no workflows configured"])
                       :subheading (html
                                    [:span
                                     "To orchestrate multiple jobs, add the workflows key to your config.yml file."
                                     [:br]
                                     " See "
                                     [:a {:href "https://circleci.com/docs/2.0/workflows"}
                                      "the workflows documentation"]
                                     " for examples and instructions."])}))}))))))

(def project-workflow-runs (om-next/factory ProjectWorkflowRuns))

(defn- settings-link
  "An Om Next compatible version of frontend.components.header/settings-link"
  [vcs-type org repo]
  (html
   [:div.btn-icon
    [:a.header-settings-link.project-settings
     {:href (routes/v1-project-settings-path {:vcs_type vcs-type
                                              :org org
                                              :repo repo})
      :title "Project settings"}
     [:i.material-icons "settings"]]]))

(defn- legacy-branch-picker
  "Wraps the branch picker in a legacy component which can load the project data
  on mount."
  [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [projects-loaded? (seq (get-in app state/projects-path))
            current-user (get-in app state/user-path)]
        (when (and (not projects-loaded?)
                   (not (empty? current-user)))
          (api/get-projects (om/get-shared owner [:comms :api])))))

    om/IRender
    (render [_]
      (om/build aside/branch-activity-list
                app
                {:opts {:workflows? true}}))))

(defui ^:once ProjectPage
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}
     {:routed-entity/organization [:organization/vcs-type
                                   :organization/name]}
     `{(:project-for-crumb {:< :routed-entity/project}) [:project/name]}
     `{(:project-for-runs {:< :routed-entity/project}) ~(om-next/get-query ProjectWorkflowRuns)}])
  ;; TODO: Add the correct analytics properties.
  #_analytics/Properties
  #_(properties [this]
      (let [props (om-next/props this)]
        {:user (get-in props [:app/current-user :user/login])
         :view :projects
         :org (get-in props [:app/route-data :route-data/organization :organization/name])}))
  Object
  (componentDidMount [this]
    (set-page-title! "CircleCI"))
  (render [this]
    (let [{{org-name :organization/name
            vcs-type :organization/vcs-type} :routed-entity/organization
           {project-name :project/name} :project-for-crumb}
          (om-next/props this)]
      (main-template/template
       {:app (:legacy/state (om-next/props this))
        :crumbs [{:type :workflows}
                 {:type :org-workflows
                  :username org-name
                  :vcs_type vcs-type}
                 {:type :project-workflows
                  :username org-name
                  :project project-name
                  :vcs_type vcs-type}]
        :header-actions (settings-link vcs-type org-name project-name)
        :sidebar (build-legacy legacy-branch-picker (:legacy/state (om-next/props this)))
        :main-content (if-let [project (:project-for-runs (om-next/props this))]
                        (project-workflow-runs project)
                        (spinner))}))))

(defui ^:once BranchWorkflowRuns
  static om-next/IQuery
  (query [this]
    [:branch/name
     {:branch/project [:project/name
                       {:project/organization [:organization/name]}]}
     `{(:routed/page {:page/connection :branch/workflow-runs
                      :page/count 30})
       ~(om-next/get-query RunList)}
     {'[:app/route-params _] [:page/number]}])
  Object
  (render [this]
    (component
      (let [props (om-next/props this)
            {branch-name :branch/name
             {project-name :project/name
              {vcs-type :organization/vcs-type
               org-name :organization/name} :project/organization} :branch/project} props
            page (:routed/page props)
            page-num (get-in props ['[:app/route-params _] :page/number])]
        (run-list (om-next/computed
                   page
                   {:prev-page-href
                    (routes/v1-project-branch-workflows-path vcs-type org-name project-name branch-name (dec page-num))

                    :next-page-href
                    (routes/v1-project-branch-workflows-path vcs-type org-name project-name branch-name (inc page-num))

                    :empty-state
                    (card/basic
                     (empty-state/empty-state
                      {:icon (icon/workflows)
                       :heading (html [:span (empty-state/important (str project-name "/" branch-name)) " has no workflows configured"])
                       :subheading (html
                                     [:span
                                      "To orchestrate multiple jobs, add the workflows key to your config.yml file."
                                      [:br]
                                      "See "
                                      [:a {:href "https://circleci.com/docs/2.0/workflows"}
                                       "the workflows documentation"]
                                      " for examples and instructions."])}))}))))))

(def branch-workflow-runs (om-next/factory BranchWorkflowRuns))

(defui ^:once BranchPage
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}
     {:routed-entity/organization [:organization/vcs-type
                                   :organization/name]}
     {:routed-entity/project [:project/name]}
     `{(:branch-for-crumb {:< :routed-entity/branch})
       [:branch/name]}
     `{(:branch-for-runs {:< :routed-entity/branch})
       ~(om-next/get-query BranchWorkflowRuns)}])
  Object
  (componentDidMount [this]
    (set-page-title! "CircleCI"))
  (render [this]
    (let [{{org-name :organization/name
            vcs-type :organization/vcs-type} :routed-entity/organization
           {project-name :project/name} :routed-entity/project
           {branch-name :branch/name} :branch-for-crumb
           branch-for-runs :branch-for-runs}
          (om-next/props this)]
      (main-template/template
       {:app (:legacy/state (om-next/props this))
        :crumbs [{:type :workflows}
                 {:type :org-workflows
                  :username org-name
                  :vcs_type vcs-type}
                 {:type :project-workflows
                  :username org-name
                  :project project-name
                  :vcs_type vcs-type}
                 {:type :branch-workflows
                  :username org-name
                  :project project-name
                  :vcs_type vcs-type
                  :branch branch-name}]
        :header-actions (settings-link vcs-type org-name project-name)
        :sidebar (build-legacy legacy-branch-picker (:legacy/state (om-next/props this)))
        :main-content (if-let [branch (:branch-for-runs (om-next/props this))]
                        (branch-workflow-runs branch)
                        (spinner))}))))

(defui ^:once OrgWorkflowRuns
  static om-next/IQuery
  (query [this]
    [:organization/name
     :organization/vcs-type
     `{(:routed/page {:page/connection :organization/workflow-runs
                      :page/count 30})
       ~(om-next/get-query RunList)}
     {'[:app/route-params _] [:page/number]}])
  Object
  (render [this]
    (component
      (let [props (om-next/props this)
            {vcs-type :organization/vcs-type
             org-name :organization/name} props
            page (:routed/page props)
            page-num (get-in props ['[:app/route-params _] :page/number])]
        (run-list (om-next/computed
                   page
                   {:prev-page-href
                    (routes/v1-org-workflows-path vcs-type org-name (dec page-num))

                    :next-page-href
                    (routes/v1-org-workflows-path vcs-type org-name (inc page-num))

                    :empty-state
                    (card/basic
                     (empty-state/empty-state
                      {:icon (icon/workflows)
                       :heading (html [:span (empty-state/important org-name) " has no workflows defined yet"])
                       :subheading (str "Add a workflow section to one of " org-name "'s project configs to start running workflows.")}))}))))))

(def org-workflow-runs (om-next/factory OrgWorkflowRuns))

(defui ^:once OrgPage
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}
     `{(:org-for-crumb {:< :routed-entity/organization})
       [:organization/vcs-type
        :organization/name]}
     `{(:org-for-runs {:< :routed-entity/organization})
       ~(om-next/get-query OrgWorkflowRuns)}])
  ;; TODO: Add the correct analytics properties.
  #_analytics/Properties
  #_(properties [this]
      (let [props (om-next/props this)]
        {:user (get-in props [:app/current-user :user/login])
         :view :projects
         :org (get-in props [:app/route-data :route-data/organization :organization/name])}))
  Object
  (componentDidMount [this]
    (set-page-title! "CircleCI"))
  (render [this]
    (let [{{org-name :organization/name
            vcs-type :organization/vcs-type} :org-for-crumb}
          (om-next/props this)]
      (main-template/template
       {:app (:legacy/state (om-next/props this))
        :crumbs [{:type :workflows}
                 {:type :org-workflows
                  :username org-name
                  :vcs_type vcs-type}]
        :sidebar (build-legacy legacy-branch-picker (:legacy/state (om-next/props this)))
        :main-content (if-let [org (:org-for-runs (om-next/props this))]
                        (org-workflow-runs org)
                        (spinner))}))))

(defui ^:once SplashPage
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}])
  ;; TODO: Add the correct analytics properties.
  #_analytics/Properties
  #_(properties [this]
      (let [props (om-next/props this)]
        {:user (get-in props [:app/current-user :user/login])
         :view :projects
         :org (get-in props [:app/route-data :route-data/organization :organization/name])}))
  Object
  (componentDidMount [this]
    (set-page-title! "CircleCI"))
  (render [this]
    (main-template/template
     {:app (:legacy/state (om-next/props this))
      :crumbs [{:type :workflows}]
      :sidebar (build-legacy legacy-branch-picker (:legacy/state (om-next/props this)))
      :main-content (card/basic
                     (empty-state/empty-state
                      {:icon (icon/workflows)
                       :heading (html [:span "Welcome to CircleCI " (empty-state/important "Workflows")])
                       :subheading "Select a project to get started."}))})))
