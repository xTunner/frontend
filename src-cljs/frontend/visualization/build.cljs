(ns frontend.visualization.build
  (:require [frontend.models.feature :as feature]))

(defn status->color [status]
  (let [is-v2? (feature/enabled? :ui-v2)
        is-success? (= status "success")
        is-failed? (= status "failed")]
    (cond
      (and is-v2? is-success?) "#42c88a"
      (and is-v2? is-failed?) "#ed5c5c"
      is-success? "#292"
      is-failed? "#c13737"
      ; grey for v2
      is-v2? "#2c2f33"
      :else "rgb(128,128,128)")))

(defn millis->min-secs [millis]
  (let [total-secs (/ millis 1000.0)
        secs (Math/floor (mod total-secs 60))
        min (Math/floor (/ total-secs 60))]
    (str min "m:" secs "s")))

(defn max-action-end-time [step]
  (let [end-times (map (fn [action] (js/Date. (aget action "end_time")))
                       (aget step "actions"))
        max-et (apply max end-times)]
    ;;(println "max-action-end-time (max " end-times ") == " max-et)
    max-et))

(defn max-action-run-time-millis [step]
  (let [run-times (map (fn [action] (aget action "run_time_millis"))
                       (aget step "actions"))
        max-rt (apply max run-times)]
    ;;(println "max-run-time-millis (max " run-times ") == " max-rt)
    max-rt))

(defn step-time-filter [scale cut-off step]
  (let [st (-> step (:actions) (first) (:start_time) (js/Date.))
        ets (-> (map (fn [action] (js/Date. (:end_time action))) (:actions step)))
        et (apply max ets)
        step-pixels (- (scale et) (scale st))]
    (> step-pixels cut-off)))

(defn chart-build [svg scale build]
  (let [bar-height 10
        bar-pad 5
        text-height 16
        step-count (-> build (:steps) (count))
        chart-height (* (-> build (:parallel))
                        (+ bar-height bar-pad))
        long-actions (filter (partial step-time-filter scale 10) (:steps build))]
    (.attr svg "height" (+ (* (+ bar-height bar-pad) (:parallel build)) (+ text-height (* text-height (count long-actions)))))

    ;; make empty groups, one for each build step
    (-> svg (.selectAll "g") (.data (clj->js (:steps build))) (.enter) (.append "g"))

    ;; for each build step, fill out the group w/ it's actions
    (doall
     (for [inx (range 0 step-count)]
       (let [step (-> build (:steps) (get inx))
             group (-> svg (.select "g:empty") (.selectAll "rect")
                       (.data (clj->js (:actions step)))
                       (.enter))]
         ;; the time bar
         (-> group
             (.append "a") (.attr #js {"xlink:href" #(aget % "output_url")})
             (.append "rect")
             (.attr #js {"x" #(scale (js/Date. (aget % "start_time")))
                         "y" #(* (+ bar-height bar-pad) (aget % "index"))
                         "width" #(- (scale (js/Date. (aget % "end_time"))) (scale (js/Date. (aget % "start_time"))))
                         "height" bar-height
                         "fill" #(status->color (aget % "status"))}))

         (let [x #(scale (js/Date. (aget % "start_time")))
               y1 #(* (+ bar-height bar-pad) (aget % "index"))
               y2 #(+ bar-height (* (+ bar-height bar-pad) (aget % "index")))]
           ;; the start tick
           (-> group
               (.append "line")
               (.attr #js {"x1" x
                           "x2" x
                           "y1" y1
                           "y2" y2
                           "stroke" "black"
                           "stroke-width" 0.75}))))))

    (-> svg (.selectAll "text") (.data (clj->js long-actions)) (.enter) (.append "text")
        (.text #(str (aget % "name") " (" (millis->min-secs (max-action-run-time-millis %)) ")"))
        (.attr #js {"x" #(scale (js/Date. (-> % (aget "actions") (aget 0) (aget "start_time"))))
                    "y" (fn [step, i] (+ text-height chart-height (* text-height i)))
                    "fill" (fn [step, i] (if (= "config" (-> step (aget "actions") (aget 0) (aget "source")))
                                          "black"
                                          "grey"))
                    }))))

(defn visualize-timing! [elem build]
  (let [org-repo-build (-> build :vcs_url)
        svg-width 900
        svg (-> js/d3 (.select elem) (.append "svg") (.attr #js {"x" 0 "y" 0}))
        d0 (js/Date. (:start_time build))
        d1 (js/Date. (:stop_time build))
        scale (-> js/d3 (.-time) (.scale))]
    (.attr svg "width" "100%")
    (-> scale (.domain #js [d0, d1]) (.range #js [0, (* 0.8 svg-width)]))
    (println "scale maps [" d0 ", " d1 "] -> [" (scale d0) ", " (scale d1) "]")

    (chart-build svg scale build)))
