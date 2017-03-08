(ns frontend.components.pages.workflow
  (:require [frontend.components.common :as common]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.status :as status]
            [frontend.components.templates.main :as main-template]
            [frontend.datetime :as datetime]
            [frontend.utils.legacy :refer [build-legacy]]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [frontend.utils :refer [component element html]]))

(defn- status-class [run-status]
  (case run-status
    :run-status/queued :status-class/waiting
    :run-status/running :status-class/running
    :run-status/succeeded :status-class/succeeded
    :run-status/failed :status-class/failed
    :run-status/canceled :status-class/stopped))

(defui ^:once Run
  static om-next/IQuery
  (query [this]
    [:run/status
     :run/started-at
     :run/stopped-at])
  Object
  (render [this]
    (component
      (let [{:keys [run/status
                    run/started-at
                    run/stopped-at]}
            (om-next/props this)]
        (card/basic
         (element :content
           (html
            [:div
             [:.status (status/badge (status-class status) (name status))]
             [:.started-at {:title (str "Started: " (datetime/full-datetime started-at))}
              (build-legacy common/updating-duration {:start started-at} {:opts {:formatter datetime/time-ago-abbreviated}})]
             [:.duration {:title (str "Duration: " (datetime/as-duration (- stopped-at started-at)))}
              (build-legacy common/updating-duration {:start started-at
                                                      :stop stopped-at})]])))))))

(def run (om-next/factory Run))

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
     {:workflow/runs (om-next/get-query Run)}])
  Object
  (render [this]
    (card/collection
     (map run (:workflow/runs (om-next/props this))))))

(def workflow-runs (om-next/factory WorkflowRuns))

(defui ^:once Page
  static om-next/IQuery
  (query [this]
    ;; NB: Every Page *must* query for {:legacy/state [*]}, to make it available
    ;; to frontend.components.header/header. This is necessary until the
    ;; wrapper, not the template, renders the header.
    ;; See https://circleci.atlassian.net/browse/CIRCLE-2412
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
