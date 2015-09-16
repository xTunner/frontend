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
            [frontend.utils :as utils :refer-macros [inspect] :refer [select-values]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring]
            [goog.string.format]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontend.models.build :as build])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [html defrender]]))

(defn add-queued-time [build]
  (let [queued-time (max (build/queued-time build) 0)]
    (assoc build :queued_time_millis queued-time)))

(defn build-graphable [{:keys [outcome]}]
  (#{"success" "failed" "canceled"} outcome))

(defn visualize-insights-bar! [elem builds]
  (let [y-max (apply max (mapcat #(select-values % [:queued_time_millis :build_time_millis])
                                 builds))
        svg-width 900
        svg-height 250
        margins {:top 50, :right 10, :bottom 20, :left 70}
        width (- svg-width (:left margins) (:right margins))
        height (- svg-height (:top margins) (:bottom margins))
        y-scale (-> (js/d3.scale.linear)
                    (.domain #js[(- y-max) y-max])
                    (.range #js[height 0])
                    (.nice))
        y-pos-scale (-> (js/d3.scale.linear)
                        (.domain #js[0 y-max])
                        (.range #js[(y-scale 0) 0])
                        (.nice))
        y-neg-scale (-> (js/d3.scale.linear)
                        (.domain #js[0 y-max])
                        (.range #js[(y-scale 0) height])
                        (.nice))
        y-middle (y-scale 0)
        y-axis (-> (js/d3.svg.axis)
                   (.scale y-scale)
                   (.orient "left")
                   (.ticks 3)
                   (.tickFormat #(js/Math.round (/ (js/Math.abs %) 1000 60))))
        x-scale (-> (js/d3.scale.ordinal)
                    (.domain (clj->js
                              (map :build_num builds)))
                    (.rangeBands #js[0 width] 0.5))
        svg (-> js/d3
                (.select elem)
                (.html "")
                (.append "svg")
                (.attr "width" svg-width)
                (.attr "height" svg-height)
                (.append "g")
                (.attr "transform" (gstring/format "translate(%s,%s)"
                                                   (:left margins)
                                                   (:top margins))))
        enter (-> svg
                  (.selectAll "rect")
                  (.data (clj->js builds))
                  (.enter))]
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
                    "x2" width}))
    ;; positive bar
    (-> enter
        (.insert "rect")
        (.attr #js {"class" #(.-outcome %)
                    "y" #(y-pos-scale (.-build_time_millis %))
                    "x" #(x-scale (.-build_num %))
                    "width" (.rangeBand x-scale)
                    "height" #(- y-middle (y-pos-scale (.-build_time_millis %)))}))

    ;; negative (queue time) bar
    (-> enter
        (.insert "rect")
        (.attr #js {"class" "queue"
                    "y" y-middle
                    "x" #(x-scale (.-build_num %))
                    "width" (.rangeBand x-scale)
                    "height" #(do
                                (- (y-neg-scale (.-queued_time_millis %)) y-middle))}))))

(defn project-insights-bar [builds owner]
  (let [chart-builds (->> builds
                           (filter build-graphable)
                           reverse
                           (map add-queued-time))]
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
   [:div
    [:header.main-head
     [:div.head-user
      [:h1 (gstring/format "Build Status: %s/%s/%s" username reponame default_branch)]]]
    (om/build project-insights-bar recent-builds)]))

(defrender build-insights [data owner]
  (let [projects (get-in data state/projects-path)]
    (html
       [:div#build-insights
        (om/build-all project-insights projects)])))
