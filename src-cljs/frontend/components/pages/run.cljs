(ns frontend.components.pages.run
  (:require [clojure.set :as set]
            [frontend.components.build-head :as old-build-head]
            [frontend.components.build-steps :as build-steps]
            [frontend.components.common :as common]
            [frontend.components.pages.workflow :as workflow-page]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.status :as status]
            [frontend.components.templates.main :as main-template]
            [frontend.datetime :as datetime]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :refer-macros [component element html]]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.legacy :refer [build-legacy]]
            [goog.string :as gstring]
            [om.core :as om]
            [om.next :as om-next :refer-macros [defui]]))

(defn- status-class [run-status]
  (case run-status
    (:job-run-status/waiting
     :job-run-status/not-running) :status-class/waiting
    :job-run-status/running :status-class/running
    :job-run-status/succeeded :status-class/succeeded
    :job-run-status/failed :status-class/failed
    (:job-run-status/canceled
     :job-run-status/not-run) :status-class/stopped))

(defui ^:once Job
  static om-next/Ident
  (ident [this {:keys [job/id]}]
    [:job/by-id id])
  static om-next/IQuery
  (query [this]
    [:job/id
     :job/status
     :job/started-at
     :job/stopped-at
     :job/name
     {:job/run [:run/id]}])
  Object
  (render [this]
    (component
      (let [{:keys [job/id
                    job/status
                    job/started-at
                    job/stopped-at]
             job-name :job/name
             {run-id :run/id} :job/run}
            (om-next/props this)
            {:keys [selected?]} (om-next/get-computed this)]
        (card/basic
         (element :content
           (html
            [:div
             [:div.job-card-inner
              [:div.status-heading
               [:div.status-name
                [:span.job-status (status/icon (status-class status))]
                (if selected?
                  [:span.job-name job-name]
                  [:a {:href (routes/v1-job-path run-id job-name)}
                   [:span.job-name job-name]])]
               [:div.status-actions
                (button/icon {:label "Retry job-name"
                              :disabled? true}
                             [:i.material-icons "more_vert"])]]
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
                   "-")]]]]])))))))

(def job (om-next/factory Job {:keyfn :job/id}))

(defn- fetch-build [owner
                    {:keys [:build/vcs-type :build/org :build/repo :build/number]}]
  (let [build-url (gstring/format "/api/v1.1/project/%s/%s/%s/%s"
                                  (name vcs-type)
                                  org
                                  repo
                                  number)]
    (ajax/ajax :get build-url
               :build-fetch
               (om/get-shared owner [:comms :api])
               :context {:project-name (gstring/format "%s/%s" org repo)
                         :build-num number})))

(defn- build-matches-spec?
  "Returns whether a build (using legacy attributes) matches the :build/*
  attributes of a build spec."
  [spec build]
  (let [mapping {:vcs_type :build/vcs-type
                 :username :build/org
                 :reponame :build/repo
                 :build_num :build/number}]
    (= spec (-> build
                (select-keys (keys mapping))
                (set/rename-keys mapping)
                (update :build/vcs-type keyword)))))

(defn- build-page [{:keys [app job-build current-tab tab-href]} owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (when job-build
        (fetch-build owner job-build)))
    om/IWillUpdate
    (will-update [this next-props _next-state]
      (let [previous-build-spec (-> owner om/get-props :job-build)
            current-build-spec (:job-build next-props)]
        (when (not= previous-build-spec current-build-spec)
         (fetch-build owner current-build-spec))))
    om/IRender
    (render [_]
      (let [app
            ;; Strip build data from legacy app state if it doesn't match the
            ;; build spec we're currently after. This happens when old build
            ;; data is present and new build data hasn't loaded yet.
            (update-in app state/build-data-path
                       #(when (build-matches-spec? job-build (:build %)) %))]
        (html
         [:div
          [:div.job-output-tabs (let [build-data (dissoc (get-in app state/build-data-path) :container-data)]
                                  (om/build old-build-head/build-sub-head
                                            {:build-data build-data
                                             :current-tab current-tab
                                             :project-data (get-in app state/project-data-path)
                                             :user (get-in app state/user-path)
                                             :projects (get-in app state/projects-path)
                                             :scopes (get-in app state/project-scopes-path)
                                             :ssh-available? false
                                             :tab-href tab-href}))]
          [:div.card (om/build build-steps/container-build-steps
                               (assoc (get-in app state/container-data-path)
                                      :selected-container-id (state/current-container-id app))
                               {:key :selected-container-id})]])))))

(defui ^:once Page
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}
     {:app/route-params [:route-params/tab :route-params/container-id]}
     `{:routed-entity/run
       ^{:component ~workflow-page/RunRow}
       [:run/id
        {:run/project [:project/name
                       {:project/organization [:organization/vcs-type
                                               :organization/name]}]}]}
     `{(:run-for-row {:< :routed-entity/run})
       ~(om-next/get-query workflow-page/RunRow)}
     `{(:run-for-jobs {:< :routed-entity/run})
       ^{:component ~workflow-page/RunRow}
       [{(:jobs-for-jobs {:< :run/jobs}) ~(om-next/get-query Job)}
        ;; NB: We need the :component metadata and :job/id here to make sure the
        ;; merger constructs the ident successfully to merge properly. This
        ;; reflects a shortcoming in Bodhi.
        {(:jobs-for-first {:< :run/jobs}) ^{:component ~Job} [:job/id
                                                              :job/build
                                                              :job/name]}]}
     {:routed-entity/job [:job/build :job/name]}])
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
    (let [{{project-name :project/name
            {org-name :organization/name
             vcs-type :organization/vcs-type} :project/organization} :run/project
           id :run/id}
          (:routed-entity/run (om-next/props this))]
      (component
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
                    :vcs_type vcs-type}
                   {:type :workflow-run
                    :run/id id}]
          :main-content
          (element :main-content
            (let [run (:run-for-row (om-next/props this))
                  selected-job (or (not-empty
                                    (:routed-entity/job (om-next/props this)))
                                   (-> (om-next/props this)
                                       :run-for-jobs
                                       :jobs-for-first
                                       first))
                  selected-job-build (get-in (om-next/props this)
                                             (into [:legacy/state]
                                                   state/build-path))
                  jobs (cond-> (-> (om-next/props this) :run-for-jobs :jobs-for-jobs)
                         selected-job-build (assoc-in [0 :job/started-at]
                                                      (:start_time selected-job-build)))
                  selected-job-build-id (:job/build selected-job)
                  selected-job-name (:job/name selected-job)
                  route-params (:app/route-params (om-next/props this))]
              (html
               [:div
                (when-not (empty? run)
                  (workflow-page/run-row run))
                [:.jobs-and-output
                 [:.jobs
                  [:div.jobs-header
                   [:.hr-title
                    [:span "Jobs"]]]
                  (card/collection
                   (map (fn [job-data]
                          (job (om-next/computed
                                job-data
                                {:selected? (= (:job/name job-data)
                                               (:job/name selected-job))})))
                        jobs))]
                 [:.output
                  [:div.output-header
                   [:.output-title
                    [:span (gstring/format "%s #%s"
                                           selected-job-name
                                           (:build/number selected-job-build-id))]]]
                  (when selected-job-build-id
                    (build-legacy build-page
                                  {:app (:legacy/state (om-next/props this))
                                   :job-build selected-job-build-id
                                   :current-tab (:route-params/tab route-params)
                                   :tab-href #(routes/v1-job-path
                                               id
                                               selected-job-name
                                               (assoc route-params
                                                      :route-params/tab %))}))]]])))})))))
