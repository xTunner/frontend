(ns frontend.components.insights.project
  (:require [frontend.components.common :as common]
            [frontend.components.insights :as insights]
            [frontend.datetime :as datetime]
            [frontend.models.project :as project-model]
            [frontend.state :as state]
            [om.core :as om :include-macros true]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            cljs-time.extend
            cljsjs.c3)
  (:require-macros [frontend.utils :refer [html defrender]]))

(def svg-info
  {:width 425
   :height 100
   :top 10, :right 10, :bottom 10, :left 30})

(def plot-info
  {:width (- (:width svg-info) (:left svg-info) (:right svg-info))
   :height (- (:height svg-info) (:top svg-info) (:bottom svg-info))
   :max-bars 100
   :positive-y% 0.60})

(defn filter-chartable-builds [builds]
  (some->> builds
           (filter insights/build-chartable?)
           (take (:max-bars plot-info))
           reverse
           (map insights/add-queued-time)))

(defn decorate-project
  "Add keys to project related to insights - :show-insights? :sort-category :chartable-builds ."
  [plans {:keys [recent-builds] :as project}]
  (let [chartable-builds (filter-chartable-builds recent-builds)]
    (-> project
        (assoc :chartable-builds chartable-builds))))

(defn build-time-bar-chart [builds owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [el (om/get-node owner)]
        (insights/insert-skeleton el)
        (insights/visualize-insights-bar! plot-info el builds owner)))
    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      (let [el (om/get-node owner)]
        (insights/visualize-insights-bar! plot-info el builds owner)))
    om/IRender
    (render [_]
      (html
       [:div.build-time-visualization]))))

(defn build-time-line-chart [builds owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [el (om/get-node owner)
            build-times (map (fn [[build-date builds]]
                               [build-date (->> builds
                                                (map :build_time_millis)
                                                insights/median)])
                             (group-by #(-> % :start_time time-format/parse time/at-midnight) builds))]
        (js/c3.generate (clj->js {:bindto el
                                  :data {:x "date"
                                         :columns [(concat ["date"] (map first build-times))
                                                   (concat ["Build Times"] (map last build-times))]}
                                  :axis {:x {:type "timeseries"
                                             :tick {:format "%Y-%m-%d"}}}}))))
    om/IRender
    (render [_]
      (html
       [:div]))))

(defrender project-insights [state owner]
  (let [projects (get-in state state/projects-path)
        plans (get-in state state/user-plans-path)
        navigation-data (:navigation-data state)
        {:keys [branches parallel] :as project} (some->> projects
                                                         (filter #(and (= (:reponame %) (:repo navigation-data))
                                                                       (= (:username %) (:org navigation-data))))
                                                         first)
        chartable-builds (filter-chartable-builds (:recent-builds project))]
    (html
     (if (nil? chartable-builds)
       ;; Loading...
       [:div.loading-spinner-big common/spinner]

       ;; We have data to show.
       [:div.project-insights
        [:div.insights-metadata-header
         [:div.card.insights-metadata
          [:dl
           [:dt "last build"]
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
           [:dt "active branches"]
           [:dd (-> branches keys count)]]]
         [:div.card.insights-metadata
          [:dl
           [:dt "median queue"]
           [:dd (datetime/as-duration (insights/median (map :queued_time_millis chartable-builds)))]]]
         [:div.card.insights-metadata
          [:dl
           [:dt "median build"]
           [:dd (datetime/as-duration (insights/median (map :build_time_millis chartable-builds)))]]]
         [:div.card.insights-metadata
          [:dl
           [:dt "parallelism"]
           [:dd parallel
            [:button.btn.btn-xs.btn-default
             [:i.material-icons "tune"]]]]]]
        [:div.card
         (om/build build-time-bar-chart chartable-builds)]
        [:div.card
         (om/build build-time-line-chart chartable-builds)]]))))
