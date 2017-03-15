(ns frontend.components.pages.workflow
  (:require [frontend.components.common :as common]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.templates.main :as main-template]
            [frontend.datetime :as datetime]
            [frontend.routes :as routes]
            [frontend.utils.legacy :refer [build-legacy]]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [frontend.utils :refer [component element html]]))

(defn- status-class [run-status]
  (case run-status
    :run-status/waiting :status-class/waiting
    :run-status/running :status-class/running
    :run-status/succeeded :status-class/succeeded
    :run-status/failed :status-class/failed
    :run-status/canceled :status-class/stopped))

;; TODO: Move this to pieces.*, as it's used on the run page as well.
(defui ^:once RunRow
  static om-next/Ident
  (ident [this {:keys [run/id]}]
    [:run/by-id id])
  static om-next/IQuery
  (query [this]
    [:run/id
     :run/status
     :run/started-at
     :run/stopped-at
     :run/branch-name
     :run/commit-sha])
  Object
  (render [this]
    (component
      (let [{:keys [run/id
                    run/status
                    run/started-at
                    run/stopped-at
                    run/branch-name
                    run/commit-sha]}
            (om-next/props this)]
        (card/basic
         (element :content
           (html
            [:div
             [:div.status {:class (name status)}
              [:a.exception {:href (routes/v1-run {:run-id id})}
               [:span.status-icon {:class (name status)}
                (case (status-class status)
                  :status-class/failed (icon/status-failed)
                  :status-class/stopped (icon/status-canceled)
                  :status-class/succeeded (icon/status-passed)
                  :status-class/running (icon/status-running)
                  :status-class/waiting (icon/status-queued))]
               [:.status-string (name status)]]]
             [:div.run-info
              [:div.build-info-header
               [:div.contextual-identifier
                [:span "workflow-name #1234"]]]
              [:div.recent-commit-msg
               [:span.recent-log "This is the thing that triggered the run"]]]
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
                  [:span {:title (str "Duration: " (datetime/as-duration (- stopped-at started-at)))}
                   (build-legacy common/updating-duration {:start started-at
                                                           :stop stopped-at})]
                  "-")]]
              [:div.metadata-row.pull-revision
               [:span.metadata-item.pull-requests {:title "Pull Requests"}
                (icon/git-pull-request)
                [:a "#1234"]]
               [:span.metadata-item.revision
                [:i.octicon.octicon-git-commit]
                [:a (subs commit-sha 0 7)]]]]
             [:div.actions
              (button/icon {:label "Stop this workflow"
                            :disabled? true}
                           (icon/cancel-circle))
              (button/icon {:label "Retry this workflow"
                            :disabled? true}
                           (icon/rebuild))]])))))))

(def run-row (om-next/factory RunRow {:keyfn :run/id}))

(defui ^:once WorkflowRuns
  static om-next/Ident
  (ident [this props]
    [:workflow/by-org-project-and-name (merge (select-keys props [:workflow/name])
                                              (-> props
                                                  (get-in [:workflow/project])
                                                  (select-keys [:project/name]))
                                              (-> props
                                                  (get-in [:workflow/project :project/organization])
                                                  (select-keys [:organization/vcs-type
                                                                :organization/name])))])
  static om-next/IQuery
  (query [this]
    [{:workflow/project [:project/name
                         {:project/organization [:organization/vcs-type
                                                 :organization/name]}]}
     :workflow/name
     {:workflow/runs (om-next/get-query RunRow)}])
  Object
  (render [this]
          (component
            (html
              [:div
               (card/collection
                 (map run-row (reverse (sort-by :run/started-at (:workflow/runs (om-next/props this))))))]))))

(def workflow-runs (om-next/factory WorkflowRuns))

(defui ^:once Page
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}
     {:app/route-data [{:route-data/workflow (om-next/get-query WorkflowRuns)}]}])
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
    (main-template/template
     {:app (:legacy/state (om-next/props this))
      :main-content (workflow-runs (get-in (om-next/props this) [:app/route-data :route-data/workflow]))})))
