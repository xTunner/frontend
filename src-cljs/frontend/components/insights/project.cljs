(ns frontend.components.insights.project
  (:require [frontend.components.common :as common]
            [frontend.components.insights :as insights]
            [frontend.datetime :as datetime]
            [frontend.models.project :as project-model]
            [frontend.state :as state]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html defrender]]))

(defrender project-insights [state owner]
  (let [projects (get-in state state/projects-path)
        plans (get-in state state/user-plans-path)
        navigation-data (:navigation-data state)

        {:keys [chartable-builds branches parallel] :as project}
        (some->> projects
                 (filter #(and (= (:reponame %) (:repo navigation-data))
                               (= (:username %) (:org navigation-data))))
                 first
                 (insights/decorate-project plans))]
    (html
     (if (nil? chartable-builds)
       ;; Loading...
       [:div.loading-spinner-big common/spinner]

       ;; We have data to show.
       [:div.project-insights
        [:div.insights-metadata-header
         [:div.card.insights-metadata
          [:dl
           [:dt "LAST BUILD"]
           [:dd (om/build common/updating-duration
                          {:start (->> chartable-builds
                                       reverse
                                       (filter :start_time)
                                       first
                                       :start_time)}
                          {:opts {:formatter datetime/as-time-since
                                  :formatter-use-start? true}})]]]
         [:div.card.insights-metadata
          [:dl
           [:dt "ACTIVE BRANCHES"]
           [:dd (-> branches keys count)]]]
         [:div.card.insights-metadata
          [:dl
           [:dt "MEDIAN QUEUE"]
           [:dd (datetime/as-duration (insights/median (map :queued_time_millis chartable-builds)))]]]
         [:div.card.insights-metadata
          [:dl
           [:dt "MEDIAN BUILD"]
           [:dd (datetime/as-duration (insights/median (map :build_time_millis chartable-builds)))]]]
         [:div.card.insights-metadata
          [:dl
           [:dt "PARALLELISM"]
           [:dd parallel]]]]
        [:div.card
         (om/build insights/project-insights-bar chartable-builds)]]))))
