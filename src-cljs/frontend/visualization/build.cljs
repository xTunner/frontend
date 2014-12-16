(ns frontend.visualization.build)

(defn status->color [status]
  (cond
   (= status "success") "#292"
   (= status "failed") "#c13737"
   :else "rgb(128,128,128)"))

(defn millis->min-secs [millis]
  (let [total-secs (/ millis 1000.0)
        secs (Math/floor (mod total-secs 60))
        min (Math/floor (/ total-secs 60))]
    (str min "m:" secs "s")))

(defn max-action-end-time [step]
  (let [end-times (map (fn [action] (js/Date. (.-end_time action)))
                       (.-actions step))
        max-et (apply max end-times)]
    ;;(println "max-action-end-time (max " end-times ") == " max-et)
    max-et))

(defn max-action-run-time-millis [step]
  (let [run-times (map (fn [action] (.-run_time_millis action))
                       (.-actions step))
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
  (let [bar-height 6
        bar-pad 2
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
             (.append "a") (.attr #js {"xlink:href" #(.-output_url %)})
             (.append "rect")
             (.attr #js {"x" #(scale (js/Date. (.-start_time %)))
                         "y" #(* (+ bar-height bar-pad) (.-index %))
                         "width" #(- (scale (js/Date. (.-end_time %))) (scale (js/Date. (.-start_time %))))
                         "height" bar-height
                         "fill" #(status->color (.-status %))}))
         ;; the start tick
         (-> group
             (.append "rect")
             (.attr #js {"x" #(scale (js/Date. (.-start_time %)))
                         "y" #(* (+ bar-height bar-pad) (.-index %))
                         "width" 0.1
                         "height" (+ 1 bar-height)
                         "stroke" "black"
                         "stroke-width" 0.5})))))

    (-> svg (.selectAll "text") (.data (clj->js long-actions)) (.enter) (.append "text")
        (.text #(str (.-name %) " (" (millis->min-secs (max-action-run-time-millis %)) ")"))
        (.attr #js {"x" #(scale (js/Date. (-> % (.-actions) (aget 0) (.-start_time))))
                    "y" (fn [step, i] (+ text-height chart-height (* text-height i)))
                    "fill" (fn [step, i] (if (= "config" (-> step (.-actions) (aget 0) (.-source)))
                                          "black"
                                          "grey"))
                    }))))

(defn visualize-timing! [elem build]
  (let [org-repo-build (-> build :vcs_url)
        svg-width 900
        svg (-> js/d3 (.select elem) (.append "svg") (.attr #js {"x" 0 "y" 0}))
        scale (-> (js/d3.time.scale.))
        d0 (js/Date. (:start_time build))
        d1 (js/Date. (:stop_time build))
        scale (-> js/d3 (.-time) (.scale))]
    (.attr svg "width" svg-width)
    (-> scale (.domain #js [d0, d1]) (.range #js [0, (* 0.8 svg-width)]))
    (println "scale maps [" d0 ", " d1 "] -> [" (scale d0) ", " (scale d1))

    (chart-build svg scale build)))
