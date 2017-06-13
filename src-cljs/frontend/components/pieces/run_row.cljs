(ns frontend.components.pieces.run-row
  (:require [clojure.string :as string]
            [frontend.components.common :as common]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.icon :as icon]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.routes :as routes]
            [frontend.utils :refer-macros [component element html]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.legacy :refer [build-legacy]]
            [frontend.utils.vcs-url :as vcs-url]
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
    [mutation
     ;; We queue the entire page to re-read using :compassus.core/route-data.
     ;; Ideally we'd know what specifically to re-run, but there are now
     ;; several keys the new run could show up under. (Aliases also complicate
     ;; this, and solving that problem is not something we want to spend time
     ;; on yet.) Re-reading the entire query here seems like a good idea
     ;; anyhow.
     :compassus.core/route-data])))

(defn- transact-run-retry
  [component run-id jobs]
  (transact-run-mutate component `(run/retry {:run/id ~run-id :run/jobs ~jobs})))

(defn- transact-run-cancel
  [component run-id vcs-type org-name project-name]
  (transact-run-mutate component `(run/cancel {:run/id ~run-id})))


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
     {:run/errors [:workflow-error/message]}
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
                    run/errors
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
            run-status-class (if (seq errors)
                               :status-class/setup-needed
                               (status-class status))]

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
                   :status-class/setup-needed (icon/status-setup-needed)
                   :status-class/stopped (icon/status-canceled)
                   :status-class/succeeded (icon/status-passed)
                   :status-class/running (icon/status-running)
                   :status-class/waiting (icon/status-queued))]
                [:.status-string (if (seq errors)
                                   "needs setup"
                                   (string/replace (name status) #"-" " "))]]]
              (cond (cancelable-statuses status)
                    [:div.cancel-button {:on-click #(transact-run-cancel this id vcs-type org-name project-name)}
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
