(ns frontend.components.pages.workflow
  (:require [cljs.pprint :as pprint]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros [frontend.utils :refer [html]]))

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
     {:workflow/runs [:run/status
                      :run/started-at
                      :run/stopped-at]}])
  Object
  (render [this]
    (html [:code [:pre (with-out-str (pprint/pprint (dissoc (om-next/props this) :legacy/state)))]])))

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
    (html
     [:div
      (workflow-runs (get-in (om-next/props this) [:app/route-data :route-data/workflow]))])))
