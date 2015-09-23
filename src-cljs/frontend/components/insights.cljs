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

(defn visualize-insights-bar! [elem builds]
  (let [y-max (apply max (mapcat #((juxt :queued_time_minutes :build_time_minutes) %)
                                 builds))
        svg-info {:width 900
                  :height 180
                  :top 30, :right 10, :bottom 10, :left 70}
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
        x-scale (-> (js/d3.scale.ordinal)
                    (.domain (clj->js
                              (map :build_num builds)))
                    (.rangeBands #js[0 (:width plot-info)] 0.5))
        svg (-> js/d3
                (.select elem)
                (.html "")
                (.append "svg")
                (.attr "width" (:width svg-info))
                (.attr "height" (:height svg-info))
                (.append "g")
                (.attr "transform" (gstring/format "translate(%s,%s)"
                                                   (:left svg-info)
                                                   (:top svg-info))))
        enter (-> svg
                  (.selectAll "rect")
                  (.data (clj->js builds))
                  (.enter))
        grid-lines-container (-> svg
                                 (.append "g")
                                 (.attr "class" "grid-line horizontal"))]

    ;; legend
    (add-legend svg plot-info)

    ;; y-axis
    (-> svg
        (.append "g")
        (.attr "class" "y-axis axis")
        (.call y-axis))
    ;; x-axis
    (-> svg
        (.append "g")
        (.attr "class" "x-axis axis")
        (.append "line")
        (.attr #js {
                    "y1" (int y-middle)
                    "y2" (int y-middle)
                    "x1" 0
                    "x2" (:width plot-info)}))

    ;; horizontal grid-lines
    (doseq [tick (remove zero? y-tick-values)
            :let [y-pos (y-scale tick)]]
      (-> grid-lines-container
          (.append "line")
          (.attr #js {
                      "y1" y-pos
                      "y2" y-pos
                      "x1" 0
                      "x2" (:width plot-info)})))

    ;; positive bar
    (-> enter
        (.insert "rect")
        (.attr #js {"class" #(str "bar " (aget % "outcome"))
                    "y" #(y-pos-scale (aget % "build_time_minutes"))
                    "x" #(x-scale (aget % "build_num"))
                    "width" (.rangeBand x-scale)
                    "height" #(- y-middle (y-pos-scale (aget % "build_time_minutes")))}))

    ;; negative (queue time) bar
    (-> enter
        (.insert "rect")
        (.attr #js {"class" "bar queue"
                    "y" y-middle
                    "x" #(x-scale (aget % "build_num"))
                    "width" (.rangeBand x-scale)
                    "height" #(- (y-neg-scale (aget % "queued_time_minutes")) y-middle)}))))

(defn chartable-builds [builds]
  (->> builds
       (filter build-graphable)
       reverse
       (map add-queued-time)
       (map add-minute-measures)))

(defn project-insights-bar [builds owner]
  (let [chart-builds (chartable-builds builds)]
    (reify
      om/IDidUpdate
      (did-update [_ prev-props prev-state]
        (let [el (om/get-node owner)]
          (visualize-insights-bar! el chart-builds)))
      om/IRender
      (render [_]
        (when-not (empty? chart-builds)
          (html
           [:div.build-time-visualization]))))))

(defrender project-insights [{:keys [reponame username default_branch recent-builds]} owner]
  (html
   [:div.project-block
    [:h1 (gstring/format "Build Status: %s/%s/%s" username reponame default_branch)]
    (om/build project-insights-bar recent-builds)]))

(defrender build-insights [data owner]
  (let [projects (get-in data state/projects-path)]
    (html
       [:div#build-insights
        [:header.main-head
         [:div.head-user
          [:h1 "Insights » Repositories"]]]
        (om/build-all project-insights projects)])))
