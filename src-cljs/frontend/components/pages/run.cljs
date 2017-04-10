(ns frontend.components.pages.run
  (:require [frontend.components.build-head :as old-build-head]
            [frontend.components.build-steps :as build-steps]
            [frontend.components.common :as common]
            [frontend.components.pieces.button :as button]
            [frontend.components.pages.workflow :as workflow-page]
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
    :job-run-status/waiting :status-class/waiting
    :job-run-status/running :status-class/running
    :job-run-status/succeeded :status-class/succeeded
    :job-run-status/failed :status-class/failed
    :job-run-status/canceled :status-class/stopped))

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
     {:app/route-data [{:route-data/run
                        ;; FIXME Merging two queries like this is a bad idea.
                        ;; The best solution people have right now is
                        ;; placeholder keys in the query, which requires
                        ;; reworking the parser. We'll probably want to do that,
                        ;; but this is the fast way to get something on the
                        ;; screen for a prototype.
                        (into (om-next/get-query workflow-page/RunRow)
                              [{:run/jobs (into [:job/build
                                                 :job/name]
                                                (om-next/get-query Job))}])}]}])
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
          (let [run (get-in (om-next/props this) [:app/route-data :route-data/run])
                first-job (-> run :run/jobs first)
                first-job-build (:job/build first-job)
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
                 (map job (:run/jobs run)))]
               [:.output
                [:div.output-header
                 [:.output-title
                  [:span (gstring/format "%s #%s"
                                         first-job-name
                                         (:build/number first-job-build))]]
                 (button/icon {:label (gstring/format "Retry %s"
                                                      first-job-name)
                               :disabled? true}
                              (icon/rebuild))
                 #_(button/button {:kind :primary
                                 :size :medium
                                 :label (gstring/format "Retry %s"
                                                        first-job-name)}
                                [:span.iconed-button (icon/rebuild) "Retry"])]
                (when first-job-build
                  (build-legacy build-page (assoc (:legacy/state (om-next/props this))
                                                  :job-build
                                                  first-job-build)))]]])))}))))
