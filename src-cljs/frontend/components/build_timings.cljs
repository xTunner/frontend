(ns frontend.components.build-timings
  (:require [om.core :as om :include-macros true]
            [frontend.models.build :as build])
  (:require-macros [frontend.utils :refer [html]]))

(def timings-width 800)

(defn create-x-scale [start-time stop-time]
  (let [start-time (js/Date. start-time)
        stop-time  (js/Date. stop-time)]
    (-> (js/d3.time.scale)
        (.domain #js [start-time stop-time])
        (.range  #js [0 timings-width]))))

(defn create-root-svg []
  (-> (.select js/d3 ".build-timings")
      (.append "svg")
      (.attr "width" timings-width)))

(defn draw-containers! [x-scale step]
  (let [bar-height 10
        step-length    #(- (x-scale (js/Date. (aget % "end_time")))
                           (x-scale (js/Date. (aget % "start_time"))))
        container-pos  #(* (+ 2 bar-height) (inc (aget % "index")))
        step-start-pos #(x-scale (js/Date. (aget % "start_time")))]
    (-> step
        (.selectAll "rect")
          (.data #(aget % "actions"))
        (.enter)
          (.append "rect")
          (.attr "width" step-length)
          (.attr "height" bar-height)
          (.attr "y" container-pos)
          (.attr "x" step-start-pos))))

(defn draw-steps! [x-scale chart steps]
  (let [step (-> chart
                 (.selectAll "g")
                   (.data (clj->js steps))
                 (.enter)
                   (.append "g"))]
    (draw-containers! x-scale step)))

(defn draw-chart! [{:keys [parallel steps start_time stop_time] :as build}]
  (let [x-scale (create-x-scale start_time stop_time)
        chart   (create-root-svg)]
    (println "Running " parallel "x with " (count steps) "steps.")
    (draw-steps! x-scale chart steps)))

(defn build-timings [build owner]
  (reify
    om/IDidMount
    (did-mount [_]
     (draw-chart! build))
    om/IRender
    (render [_]
      (html
       [:div.build-timings]))))
