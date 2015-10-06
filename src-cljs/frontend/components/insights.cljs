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

(def svg-info
  {:width 900
   :height 180
   :top 30, :right 10, :bottom 10, :left 70})

(def plot-info
  {:width (- (:width svg-info) (:left svg-info) (:right svg-info))
   :height (- (:height svg-info) (:top svg-info) (:bottom svg-info))
   :max-bars 60
   :left-legend-items [{:classname "success"
                        :text "Passed"}
                       {:classname "failed"
                        :text "Failed"}
                       {:classname "canceled"
                        :text "Canceled"}]
   :right-legend-items [{:classname "queue"
                         :text "Queue time"}]
   :legend-info {:square-size 10
                 :item-width 80
                 :item-height 14   ; assume font is 14px
                 :spacing 4}})

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

(defn add-legend [svg]
  (let [{:keys [square-size item-width item-height spacing]} (:legend-info plot-info)
        left-legend-enter (-> svg
                             (.select ".legend-container")
                             (.selectAll ".legend")
                             (.data (clj->js (:left-legend-items plot-info)))
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
        right-legend-enter (-> svg
                              (.select ".legend-container")
                              (.selectAll ".legend-right")
                              (.data (clj->js (:right-legend-items plot-info)))
                              (.enter)
                              (.append "g")
                              (.attr #js {"class" "legend right"
                                          "transform"
                                          (fn [item i]
                                            (let [tr-x (- (:width plot-info) (* (inc i) item-width))
                                                  tr-y (- (- item-height
                                                             (/ (- item-height square-size)
                                                                2)))]
                                              (gstring/format "translate(%s,%s)" tr-x  tr-y)))}))]
    ;; left legend
    (-> left-legend-enter
        (.append "rect")
        (.attr #js {"width" square-size
                    "height" square-size
                    ;; `aget` must be used here instead of direct field access.  See note in preamble.
                    "class" #(aget % "classname")
                    "transform"
                    (fn [item i]
                      (gstring/format "translate(%s,%s)" 0  (- (+ square-size))))}))
    (-> left-legend-enter
        (.append "text")
        (.attr #js {"x" (+ square-size spacing)})
        (.text #(aget % "text")))

    ;; right legend
    (-> right-legend-enter
        (.append "rect")
        (.attr #js {"width" square-size
                    "height" square-size
                    "class" #(aget % "classname")
                    "transform"
                    (fn [item i]
                      (gstring/format "translate(%s,%s)" 0  (- (+ square-size))))}))
    (-> right-legend-enter
        (.append "text")
        (.attr #js {"x" (+ square-size
                           spacing)})
        (.text #(aget % "text")))))

(defn visualize-insights-bar! [el builds]
  (let [y-max (apply max (mapcat #((juxt :queued_time_minutes :build_time_minutes) %)
                                 builds))
        y-scale (-> (js/d3.scale.linear)
                    (.domain #js[(- y-max) y-max])
                    (.range #js[(:height plot-info) 0]))
        y-pos-scale (-> (js/d3.scale.linear)
                        (.domain #js[0 y-max])
                        (.range #js[(y-scale 0) 0]))
        y-neg-scale (-> (js/d3.scale.linear)
                        (.domain #js[0 y-max])
                        (.range #js[(y-scale 0) (:height plot-info)]))
        y-middle (y-scale 0)
        y-tick-values (map js/Math.round
                           [y-max (* y-max 0.5) 0 (* -1 y-max 0.5) (- y-max)])
        y-axis (-> (js/d3.svg.axis)
                   (.scale y-scale)
                   (.orient "left")
                   (.tickValues (clj->js y-tick-values))
                   (.tickFormat js/Math.abs))
        scale-filler (map (partial str "xx-") (range (- (:max-bars plot-info) (count builds))))
        x-scale (-> (js/d3.scale.ordinal)
                    (.domain (clj->js
                              `(~@(map :build_num builds) ~@scale-filler)))
                    (.rangeBands #js[0 (:width plot-info)] 0.5))
        svg (-> js/d3
                (.select el)
                (.select "svg g.plot-area"))
        bars-join (-> svg
                      (.select "g > g.bars")
                      (.selectAll "g.bar-pair")
                      (.data (clj->js builds)))
        bars-enter-g (-> bars-join
                         (.enter)
                         (.append "g")
                         (.attr "class" "bar-pair"))
        grid-y-vals (for [tick (remove zero? y-tick-values)] (y-scale tick))
        grid-lines-join (-> svg
                            (.select "g.grid-lines")
                            (.selectAll "line.horizontal")
                            (.data (clj->js grid-y-vals)))]

    ;; top bar enter
    (-> bars-enter-g
        (.append "rect")
        (.attr "class" "bar top")
        (.insert "title"))

    ;; bottom (queue time) bar enter
    (-> bars-enter-g
        (.append "rect")
        (.attr "class" "bar bottom queue")
        (.insert "title"))

    ;; top bars enter and update
    (-> bars-join
        (.select ".top")
        (.attr #js {"class" #(str "bar top " (aget % "outcome"))
                    "y" #(y-pos-scale (aget % "build_time_minutes"))
                    "x" #(x-scale (aget % "build_num"))
                    "width" (.rangeBand x-scale)
                    "height" #(- y-middle (y-pos-scale (aget % "build_time_minutes")))})
        (.select "title")
        (.text #(gstring/format "%s in %.1fm" (gstring/toTitleCase (aget % "outcome")) (aget % "build_time_minutes"))))

    ;; bottom bar enter and update
    (-> bars-join
        (.select ".bottom")
        (.attr #js {"y" y-middle
                    "x" #(x-scale (aget % "build_num"))
                    "width" (.rangeBand x-scale)
                    "height" #(- (y-neg-scale (aget % "queued_time_minutes")) y-middle)})
        (.select "title")
        (.text #(gstring/format "Queue time %.1fm" (aget % "queued_time_minutes"))))

    ;; bars exit
    (-> bars-join
        (.exit)
        (.remove))


    ;; legend
    (add-legend svg)

    ;; y-axis
    (-> svg
        (.select ".axis-container g.y-axis.axis")
        (.call y-axis))
    ;; x-axis
    (-> svg
        (.select ".axis-container g.axis.x-axis line")
        (.attr #js {"y1" (int y-middle)
                    "y2" (int y-middle)
                    "x1" 0
                    "x2" (:width plot-info)}))

    ;; grid lines enter
    (-> grid-lines-join
        (.enter)
        (.append "line"))
    ;; grid lines enter and update
    (-> grid-lines-join
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
       (map add-minute-measures)
       (take (:max-bars plot-info))))

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

(defrender build-insights [data owner]
  (let [projects (get-in data state/projects-path)]
    (html
       [:div#build-insights
        [:header.main-head
         [:div.head-user
          [:h1 "Insights » Repositories"]]]
        (om/build-all project-insights projects)])))
