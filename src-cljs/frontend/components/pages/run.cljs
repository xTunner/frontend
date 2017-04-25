(ns frontend.components.pages.run
  (:require [frontend.components.build-head :as old-build-head]
            [frontend.components.build-steps :as build-steps]
            [frontend.components.common :as common]
            [frontend.components.pages.workflow :as workflow-page]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.status :as status]
            [frontend.components.templates.main :as main-template]
            [frontend.datetime :as datetime]
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
     :job/name])
  Object
  (render [this]
    (component
      (let [{:keys [job/id
                    job/status
                    job/started-at
                    job/stopped-at]
             job-name :job/name}
            (om-next/props this)]
        (card/basic
         (element :content
           (html
            [:div
             [:div.job-card-inner
              [:div.status-heading
               [:div.status-name
                [:span.job-status (status/icon (status-class status))]
                [:span.job-name job-name]]
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

(defn- build-page [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [{:keys [:build/vcs-type :build/org :build/repo :build/number]} (:job-build app)
            build-url (gstring/format "/api/v1.1/project/%s/%s/%s/%s"
                                      (name vcs-type)
                                      org
                                      repo
                                      number)]
        (ajax/ajax :get build-url
                   :build-fetch
                   (om/get-shared owner [:comms :api])
                   :context {:project-name (gstring/format "%s/%s" org repo)
                             :build-num number})))
    om/IRender
    (render [_]
      (html
       [:div
         [:div.job-output-tabs (om/build old-build-head/build-sub-head {:build-data (dissoc (get-in app state/build-data-path) :container-data)
                                                  :current-tab (get-in app state/navigation-tab-path)
                                                  :container-id (state/current-container-id app)
                                                  :project-data (get-in app state/project-data-path)
                                                  :user (get-in app state/user-path)
                                                  :projects (get-in app state/projects-path)
                                                  :scopes (get-in app state/project-scopes-path)
                                                  :ssh-available? false})]
         [:div.card (om/build build-steps/container-build-steps
                   (assoc (get-in app state/container-data-path)
                          :selected-container-id (state/current-container-id app))
                   {:key :selected-container-id})]]))))

(defui ^:once Page
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}
     `{(:run-for-row {:< :route-params/run})
       ~(om-next/get-query workflow-page/RunRow)}
     `{(:run-for-jobs {:< :route-params/run})
       ^{:component ~workflow-page/RunRow}
       [{(:jobs-for-jobs {:< :run/jobs}) ~(om-next/get-query Job)}
        ;; NB: We need the :component metadata and :job/id here to make sure the
        ;; merger constructs the ident successfully to merge properly. This
        ;; reflects a shortcoming in Bodhi.
        {(:jobs-for-first {:< :run/jobs}) ^{:component ~Job} [:job/id
                                                              :job/build
                                                              :job/name]}]}])
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
    (component
      (main-template/template
       {:app (:legacy/state (om-next/props this))
        :main-content
        (element :main-content
          (let [run (:run-for-row (om-next/props this))
                first-job (-> (om-next/props this) :run-for-jobs :jobs-for-first first)
                first-job-build (get-in (om-next/props this)
                                        (into [:legacy/state]
                                              state/build-path))
                jobs (cond-> (-> (om-next/props this) :run-for-jobs :jobs-for-jobs)
                       first-job-build (assoc-in [0 :job/started-at]
                                                 (:start_time first-job-build)))

                first-job-build-id (:job/build first-job)
                first-job-name (:job/name first-job)]
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
                 (map job jobs))]
               [:.output
                [:div.output-header
                 [:.output-title
                  [:span (gstring/format "%s #%s"
                                         first-job-name
                                         (:build/number first-job-build-id))]]]
                (when first-job-build-id
                  (build-legacy build-page (assoc (:legacy/state (om-next/props this))
                                                  :job-build
                                                  first-job-build-id)))]]])))}))))
