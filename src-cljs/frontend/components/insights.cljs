(ns frontend.components.insights
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as string]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.forms :refer [managed-button]]
            [frontend.datetime :as datetime]
            [frontend.models.repo :as repo-model]
            [frontend.models.user :as user-model]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [inspect]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.routes :as routes]
            [goog.string :as gstring]
            [goog.string.format]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontend.models.build :as build])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [html defrender]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;                                                                         ;;
;; Note that unexterned properties must be accessed with `aget` instead of ;;
;; the `.-` or `..` shortcut notations.                                    ;;
;;                                                                         ;;
;; Google closure compiler with "advanced" optimizations will mangle       ;;
;; unexterned field names.                                                 ;;
;;                                                                         ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def svg-info
  {:width 900
   :height 180
   :top 30, :right 10, :bottom 10, :left 70})

(defn add-queued-time [build]
  (let [queued-time (max (build/queued-time build) 0)]
    (assoc build :queued_time_millis queued-time)))

(defn add-minute-measures [build]
  (let [key-mapping {:queued_time_minutes :queued_time_millis
                     :build_time_minutes :build_time_millis}]
    (reduce-kv (fn [accum new-key old-key]
                 (assoc accum new-key (/ (old-key build) 1000 60)))
               build
               key-mapping)))

(defn build-graphable [{:keys [outcome]}]
  (#{"success" "failed" "canceled"} outcome))

(defn add-legend [svg {chart-width :width}]
  (let [left-legend-array [{:classname "success"
                            :text "Passed"}
                           {:classname "failed"
                            :text "Failed"}
                           {:classname "canceled"
                            :text "Canceled"}]
        {:keys [square-size item-width item-height spacing]} {:square-size 10
                                                              :item-width 80
                                                              :item-height 14   ; assume font is 14px
                                                              :spacing 4}
        right-legend-array [{:classname "queue"
                             :text "Queue time"}]
        left-legend-item (-> svg
                             (.select ".legend-container")
                             (.selectAll ".legend")
                             (.data (clj->js left-legend-array))
                             (.enter)
                             (.append "g")
                             (.attr #js {"class" "legend left"
                                         "transform"
                                         (fn [item i]
                                           (let [tr-x (* i item-width)
                                                 tr-y (- (- item-height
                                                            (/ (- item-height square-size)
                                                               2)))]
                                             (gstring/format "translate(%s,%s)" tr-x  tr-y)))}))
        right-legend-item (-> svg
                              (.select ".legend-container")
                              (.selectAll ".legend-right")
                              (.data (clj->js right-legend-array))
                              (.enter)
                              (.append "g")
                              (.attr #js {"class" "legend right"
                                          "transform"
                                          (fn [item i]
                                            (let [tr-x (- chart-width (* (inc i) item-width))
                                                  tr-y (- (- item-height
                                                             (/ (- item-height square-size)
                                                                2)))]
                                              (gstring/format "translate(%s,%s)" tr-x  tr-y)))}))]
    ;; left legend
    (-> left-legend-item
        (.append "rect")
        (.attr #js {"width" square-size
                    "height" square-size
                    ;; `aget` must be used here instead of direct field access.  See note in preamble.
                    "class" #(aget % "classname")
                    "transform"
                    (fn [item i]
                      (gstring/format "translate(%s,%s)" 0  (- (+ square-size))))}))
    (-> left-legend-item
        (.append "text")
        (.attr #js {"x" (+ square-size spacing)})
        (.text #(aget % "text")))

    ;; right legend
    (-> right-legend-item
        (.append "rect")
        (.attr #js {"width" square-size
                    "height" square-size
                    "class" #(aget % "classname")
                    "transform"
                    (fn [item i]
                      (gstring/format "translate(%s,%s)" 0  (- (+ square-size))))}))
    (-> right-legend-item
        (.append "text")
        (.attr #js {"x" (+ square-size
                           spacing)})
        (.text #(aget % "text")))))

(defn visualize-insights-bar! [el builds]
  (let [max-bar-count 60
        builds (take max-bar-count builds)
        y-max (apply max (mapcat #((juxt :queued_time_minutes :build_time_minutes) %)
                                 builds))
        plot-info {:width (- (:width svg-info) (:left svg-info) (:right svg-info))
                   :height (- (:height svg-info) (:top svg-info) (:bottom svg-info))}
        y-scale (-> (js/d3.scale.linear)
                    (.domain #js[(- y-max) y-max])
                    (.range #js[(:height plot-info) 0])
                    (.nice))
        y-pos-scale (-> (js/d3.scale.linear)
                        (.domain #js[0 y-max])
                        (.range #js[(y-scale 0) 0])
                        (.nice))
        y-neg-scale (-> (js/d3.scale.linear)
                        (.domain #js[0 y-max])
                        (.range #js[(y-scale 0) (:height plot-info)])
                        (.nice))
        y-middle (y-scale 0)
        y-tick-values (map js/Math.round
                           [y-max (* y-max 0.5) 0 (* -1 y-max 0.5) (- y-max)])
        y-axis (-> (js/d3.svg.axis)
                   (.scale y-scale)
                   (.orient "left")
                   (.tickValues (clj->js y-tick-values))
                   (.tickFormat js/Math.abs))
        scale-filler (map (partial str "xx-") (range (- max-bar-count (count builds))))
        x-scale (-> (js/d3.scale.ordinal)
                    (.domain (clj->js
                              `(~@(map :build_num builds) ~@scale-filler)))
                    (.rangeBands #js[0 (:width plot-info)] 0.5))
        svg (-> js/d3
                (.select el)
                (.select "svg g.plot-area"))
        bars-enter (-> svg
                       (.select "g > g.bars")
                       (.selectAll "g.bar-pair")
                       (.data (clj->js builds))
                       (.enter)
                       (.append "g")
                       (.attr "class" "bar-pair"))
        grid-lines-container (-> svg
                                 (.select "g.grid-lines"))
        grid-y-vals (for [tick (remove zero? y-tick-values)] (y-scale tick))]

    ;; positive bar
    (-> bars-enter
        (.insert "rect")
        (.attr #js {"class" #(str "bar " (aget % "outcome"))
                    "y" #(y-pos-scale (aget % "build_time_minutes"))
                    "x" #(x-scale (aget % "build_num"))
                    "width" (.rangeBand x-scale)
                    "height" #(- y-middle (y-pos-scale (aget % "build_time_minutes")))})
        (.insert "title")
        (.text #(gstring/format "%s in %.1fm" (gstring/toTitleCase (aget % "outcome")) (aget % "build_time_minutes"))))

    ;; negative (queue time) bar
    (-> bars-enter
        (.insert "rect")
        (.attr #js {"class" "bar queue"
                    "y" y-middle
                    "x" #(x-scale (aget % "build_num"))
                    "width" (.rangeBand x-scale)
                    "height" #(- (y-neg-scale (aget % "queued_time_minutes")) y-middle)})
        (.insert "title")
        (.text #(gstring/format "Queue time %.1fm" (aget % "queued_time_minutes"))))

    ;; legend
    (add-legend svg plot-info)

    ;; y-axis
    (-> svg
        (.select ".axis-container g.y-axis.axis")
        (.call y-axis))
    ;; x-axis
    (-> svg
        (.select ".axis-container g.axis.x-axis line")
        (.attr #js {
                    "y1" (int y-middle)
                    "y2" (int y-middle)
                    "x1" 0
                    "x2" (:width plot-info)}))

    ;; grid lines
    (-> grid-lines-container
        (.selectAll "line.horizontal")
        (.data (clj->js grid-y-vals))
        (.enter)
        (.append "line")
        (.attr #js {"class" "horizontal"
                    "y1" (fn [y] y)
                    "y2" (fn [y] y)
                    "x1" 0
                    "x2" (:width plot-info)}))))

(defn insert-skeleton [el]
  (let [plot-area (-> js/d3
                      (.select el)
                      (.append "svg")
                      (.attr "width" (:width svg-info))
                      (.attr "height" (:height svg-info))
                      (.append "g")
                      (.attr "class" "plot-area")
                      (.attr "transform" (gstring/format "translate(%s,%s)"
                                                         (:left svg-info)
                                                         (:top svg-info))))]

    (-> plot-area
        (.append "g")
        (.attr "class" "legend-container"))
    (-> plot-area
        (.append "g")
        (.attr "class" "grid-lines"))
    (-> plot-area
        (.append "g")
        (.attr "class" "bars"))

    (let [axis-container (-> plot-area
                             (.append "g")
                             (.attr "class" "axis-container"))]
      (-> axis-container
          (.append "g")
          (.attr "class" "x-axis axis")
          (.append "line"))
      (-> axis-container
          (.append "g")
          (.attr "class" "y-axis axis")))))

(defn chartable-builds [builds]
  (->> builds
       (filter build-graphable)
       reverse
       (map add-queued-time)
       (map add-minute-measures)))

(defn project-insights-bar [builds owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [el (om/get-node owner)]
        (insert-skeleton el)
        (visualize-insights-bar! el builds)))
    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      (let [el (om/get-node owner)]
        (visualize-insights-bar! el builds)))
    om/IRender
    (render [_]
      (html
       [:div.build-time-visualization]))))

(defrender project-insights [{:keys [reponame username default_branch recent-builds]} owner]
  (let [builds (chartable-builds recent-builds)]
    (when (not-empty builds)
      (html
       [:div.project-block
        [:h1 (gstring/format "Build Status: %s/%s/%s" username reponame default_branch)]
        (om/build project-insights-bar builds)]))))

(defrender no-projects [data owner]
  (html
    [:div.no-insights-block
     [:div.content
      [:div.row
       [:div.header.text-center "No Insights yet"]]
       [:div.details.text-center "Add projects from your Github orgs and start building on CircleCI to view insights."]
      [:div.row.text-center
       [:a.btn.btn-success {:href (routes/v1-add-projects)} "Add Project"]]]]))

(defrender build-insights [data owner]
  (let [projects     (get-in data state/projects-path)
        loading?     (get-in data state/projects-loading-path)
        no-projects? (and (not loading?) (empty? projects))]
    (html
       [:div#build-insights
        [:header.main-head
         [:div.head-user
          [:h1 "Insights » Repositories"]]]
        (cond
          loading?     [:div.loading-spinner common/spinner]
          no-projects? (om/build no-projects data)
          :else        (om/build-all project-insights projects))])))
