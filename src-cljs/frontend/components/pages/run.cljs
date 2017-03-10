(ns frontend.components.pages.run
  (:require [frontend.components.common :as common]
            [frontend.components.pages.workflow :as workflow-page]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.status :as status]
            [frontend.components.templates.main :as main-template]
            [frontend.datetime :as datetime]
            [frontend.utils.legacy :refer [build-legacy]]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [frontend.utils :refer [component element html]]))

(defn- status-class [run-status]
  (case run-status
    :job-run-status/waiting :status-class/waiting
    :job-run-status/running :status-class/running
    :job-run-status/succeeded :status-class/succeeded
    :job-run-status/failed :status-class/failed
    :job-run-status/canceled :status-class/stopped))

(defui ^:once JobRun
  static om-next/Ident
  (ident [this {:keys [job-run/id]}]
    [:job-run/by-id id])
  static om-next/IQuery
  (query [this]
    [:job-run/id
     :job-run/status
     :job-run/started-at
     :job-run/stopped-at
     {:job-run/job [:job/name]}])
  Object
  (render [this]
    (component
      (let [{:keys [job-run/id
                    job-run/status
                    job-run/started-at
                    job-run/stopped-at]
             {job-name :job/name} :job-run/job}
            (om-next/props this)]
        (card/basic
         (element :content
           (html
            [:div
             (status/icon (status-class status))
             job-name
             [:div.metadata
              [:div.metadata-row.timing
               [:span.metadata-item.recent-time.start-time
                [:i.material-icons "today"]
                (if started-at
                  [:span {:title (str "Started: " (datetime/full-datetime started-at))}
                   (build-legacy common/updating-duration {:start started-at} {:opts {:formatter datetime/time-ago-abbreviated}})
                   " ago"]
                  "-")]
               [:span.metadata-item.recent-time.duration
                [:i.material-icons "timer"]
                (if stopped-at
                  [:span {:title (str "Duration: " (datetime/as-duration (- stopped-at started-at)))}
                   (build-legacy common/updating-duration {:start started-at
                                                           :stop stopped-at})]
                  "-")]]]])))))))

(def job-run (om-next/factory JobRun {:keyfn :job-run/id}))

(defui ^:once Page
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}
     {:app/route-data [{:route-data/run #_(om-next/get-query Run)
                        #_[:run/id
                           #_{:run/workflow []}]
                        ;; FIXME Merging two queries like this is a bad idea.
                        ;; The best solution people have right now is
                        ;; placeholder keys in the query, which requires
                        ;; reworking the parser. We'll probably want to do that,
                        ;; but this is the fast way to get something on the
                        ;; screen for a prototype.
                        (into (om-next/get-query workflow-page/RunRow)
                              [{:run/job-runs (om-next/get-query JobRun)}])}]}])
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
      :main-content
      (let [run (get-in (om-next/props this) [:app/route-data :route-data/run])]
        (html
         [:div
          (when-not (empty? run)
            (workflow-page/run-row run))
          (card/collection
           (map job-run (:run/job-runs run)))]))})))
