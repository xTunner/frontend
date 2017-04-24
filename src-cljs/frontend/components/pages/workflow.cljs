(ns frontend.components.pages.workflow
  (:require [clojure.string :as string]
            [frontend.components.common :as common]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.templates.main :as main-template]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.routes :as routes]
            [frontend.utils :refer-macros [component element html]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.legacy :refer [build-legacy]]
            [om.next :as om-next :refer-macros [defui]]))

(defn- status-class [run-status]
  (case run-status
    :run-status/not-run :status-class/waiting
    :run-status/running :status-class/running
    :run-status/succeeded :status-class/succeeded
    :run-status/failed :status-class/failed
    :run-status/canceled :status-class/stopped))

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
  [sha]
  (html
   (when sha
     (let [pretty-sha (build-model/github-revision {:vcs_revision sha})]
       [:span.metadata-item.revision
        [:i.octicon.octicon-git-commit]
        [:a {:title pretty-sha
             :href (build-model/commit-url {:vcs_revision sha})}
         pretty-sha]]))))

;; TODO: Move this to pieces.*, as it's used on the run page as well.
(defui ^:once RunRow
  static om-next/Ident
  (ident [this props]
    [:run/by-id (:run/id props)])
  static om-next/IQuery
  (query [this]
    [:run/id
     :run/name
     :run/status
     :run/started-at
     :run/stopped-at
     :run/branch-name
     {:run/trigger-info [:trigger-info/vcs-revision
                         :trigger-info/subject
                         :trigger-info/body
                         :trigger-info/branch
                         {:trigger-info/pull-requests [:pull-request/url]}]}])
  Object
  (render [this]
    (component
      (let [{:keys [run/id
                    run/status
                    run/started-at
                    run/stopped-at
                    run/trigger-info]
             run-name :run/name}
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
             [:div.status {:class (name run-status-class)}
              [:a.exception {:href (routes/v1-run {:run-id id})}
               [:span.status-icon {:class (name run-status-class)}
                (case (status-class status)
                  :status-class/failed (icon/status-failed)
                  :status-class/stopped (icon/status-canceled)
                  :status-class/succeeded (icon/status-passed)
                  :status-class/running (icon/status-running)
                  :status-class/waiting (icon/status-queued))]
               [:.status-string (string/replace (name status) #"-" " ")]]]
             [:div.run-info
              [:div.build-info-header
               [:div.contextual-identifier
                [:a {:href (routes/v1-run {:run-id id})}
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
               (commit-link commit-sha)]]
             [:div.actions
              (button/icon {:label "Stop this workflow"
                            :disabled? true}
                           (icon/cancel-circle))
              (button/icon {:label "Retry this workflow"
                            :disabled? true}
                           (icon/rebuild))]])))))))

(def run-row (om-next/factory RunRow {:keyfn :run/id}))

(defui ^:once WorkflowRuns
  static om-next/IQuery
  (query [this]
    [{:project/workflow-runs (om-next/get-query RunRow)}])
  Object
  (render [this]
    (component
      (html
       [:div
        (card/collection
         (map run-row (:project/workflow-runs (om-next/props this))))]))))

(def workflow-runs (om-next/factory WorkflowRuns))

(defui ^:once Page
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}
     {:app/route-data [{:route-data/workflow
                        (into [:project/name
                               {:project/organization [:organization/vcs-type
                                                       :organization/name]}]
                              (om-next/get-query WorkflowRuns))}]}])
  ;; TODO: Add the correct analytics properties.
  #_analytics/Properties
  #_(properties [this]
      (let [props (om-next/props this)]
        {:user (get-in props [:app/current-user :user/login])
         :view :projects
         :org (get-in props [:app/route-data :route-data/organization :organization/name])}))
  Object
  ;; TODO: Title this page.
  #_(componentDidMount [this]
      (set-page-title! "Projects"))
  (render [this]
    (let [{{{:as route-data
             project-name :project/name
             {org-name :organization/name
              vcs-type :organization/vcs-type} :project/organization} :route-data/workflow} :app/route-data}
          (om-next/props this)]
     (main-template/template
      {:app (:legacy/state (om-next/props this))
       :crumbs [{:type :dashboard}
                {:type :org
                 :username org-name
                 :vcs_type vcs-type}
                {:type :project
                 :username org-name
                 :project project-name
                 :vcs_type vcs-type}
                {:type :project-workflows
                 :username org-name
                 :project project-name
                 :vcs_type vcs-type}]
       :main-content (workflow-runs route-data)}))))
