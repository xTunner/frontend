(ns frontend.components.pages.run
  (:require [frontend.components.pages.workflow :as workflow-page]
            [frontend.components.templates.main :as main-template]
            [om.next :as om-next :refer-macros [defui]]))

(defui ^:once Page
  static om-next/IQuery
  (query [this]
    ['{:legacy/state [*]}
     {:app/route-data [{:route-data/run #_(om-next/get-query Run)
                        #_[:run/id
                           #_{:run/workflow []}]
                        (om-next/get-query workflow-page/RunRow)}]}])
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
        (when-not (empty? run)
          (workflow-page/run-row run)))})))
