(ns frontend.components.build-timings
  (:require [om.core :as om :include-macros true]
            [goog.string :as gstring]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build]
            [frontend.models.project :as project-model]
            [frontend.routes :as routes]
            [frontend.components.common :as common]
            [goog.events :as gevents]
            [frontend.disposable :as disposable])
  (:require-macros [frontend.utils :refer [html]]))

(def padding-right 20)

(def top-axis-height 20)
(def left-axis-width 40)

(def bar-height 20)
(def bar-gap 10)
(def container-bar-height (- bar-height bar-gap))
(def step-start-line-extension 1)

(def min-container-rows 4)

(defn timings-width []  (-> (.querySelector js/document ".build-timings")
                            (.-offsetWidth)
                            (- padding-right)
                            (- left-axis-width)))

(defn timings-height [number-of-containers]
  (let [number-of-containers (if (< number-of-containers min-container-rows)
                               min-container-rows
                               number-of-containers)]
  (* (inc number-of-containers) bar-height)))

;;; Helpers
(defn create-x-scale [start-time stop-time]
  (let [start-time (js/Date. start-time)
        stop-time  (js/Date. stop-time)]
    (-> (js/d3.time.scale)
        (.domain #js [start-time stop-time])
        (.range  #js [0 (timings-width)]))))

(defn create-root-svg [number-of-containers]
  (let [root (.select js/d3 "#build-timings")]
    (-> root
        (.attr "width" (+ (timings-width)
                         left-axis-width
                         padding-right))
        (.attr "height" (+ (timings-height number-of-containers)
                          top-axis-height)))
    (-> root
        (.select "g")
        (.remove))

    (-> root
        (.append "g")
        (.attr "transform" (gstring/format "translate(%d,%d)" left-axis-width top-axis-height)))))

(defn create-y-axis [number-of-containers]
  (let [range-start (+ bar-height (/ container-bar-height 2))
        range-end   (+ (timings-height number-of-containers) (/ container-bar-height 2))
        axis-scale  (-> (.linear js/d3.scale)
                          (.domain #js [0 number-of-containers])
                          (.range  #js [range-start range-end]))]
  (-> (js/d3.svg.axis)
        (.tickValues (clj->js (range 0 number-of-containers)))
        (.scale axis-scale)
        (.tickFormat #(js/Math.floor %))
        (.orient "left"))))

(defn create-x-axis [build-duration]
  (let [axis-scale (-> (.linear js/d3.scale)
                         (.domain #js [0 build-duration])
                         (.range  #js [0 (timings-width)]))]
  (-> (.axis js/d3.svg)
        (.tickValues (clj->js (range 0 (inc build-duration) (/ build-duration 4))))
        (.scale axis-scale)
        (.tickFormat #(datetime/as-duration %))
        (.orient "top"))))

(defn duration [start-time stop-time]
  (- (.getTime (js/Date. stop-time))
     (.getTime (js/Date. start-time))))

(defn container-position [step]
  (* bar-height (inc (aget step "index"))))

(defn scaled-time [x-scale step time-key]
  (x-scale (js/Date. (aget step time-key))))

(defn wrap-status [status]
  {:status  status
   :outcome status})


;;; Elements of the visualization
(defn highlight-selected-container! [step-data]
  (let [fade-value #(if (= (.-textContent %1) (str (aget %2 "index"))) 1 0.5)]
    (-> (.select js/d3 ".y-axis")
        (.selectAll ".tick")
        (.selectAll "text")
        (.transition)
        (.duration 200)
        (.attr "fill-opacity"   #(this-as element (fade-value element step-data))))))

(defn highlight-selected-step! [step selected]
  (-> step
      (.selectAll "rect")
      (.transition)
      (.duration 200)
      (.attr "fill-opacity" #(this-as element (if (= element selected) 1 0.5)))))

(defn reset-selected! [step]
  ;reset all the steps
  (-> step
      (.selectAll "rect")
      (.transition)
      (.duration 500)
      (.attr "fill-opacity" 1))

  ;reset all container labels
  (-> (.select js/d3 ".y-axis")
      (.selectAll ".tick")
      (.selectAll "text")
      (.transition)
      (.duration 200)
      (.attr "fill-opacity" 1)))

(defn draw-containers! [x-scale step]
  (let [step-length         #(- (scaled-time x-scale % "end_time")
                                (scaled-time x-scale % "start_time"))
        step-start-pos      #(x-scale (js/Date. (aget % "start_time")))
        step-duration       #(datetime/as-duration (duration (aget % "start_time") (aget % "end_time")))]
    (-> step
        (.selectAll "rect")
          (.data #(aget % "actions"))
        (.enter)
          (.append "rect")
            (.attr "class"     #(str "container-step-" (build/status-class (wrap-status (aget % "status")))))
            (.attr "width"     step-length)
            (.attr "height"    container-bar-height)
            (.attr "y"         container-position)
            (.attr "x"         step-start-pos)
            (.on   "mouseover" #(this-as selected
                                         (highlight-selected-step! step selected)
                                         (highlight-selected-container! %)))
            (.on   "mouseout"  #(reset-selected! step))
          (.append "title")
            (.text #(gstring/format "%s (%s) - %s" (aget % "name") (aget % "index") (step-duration %))))))

(defn draw-step-start-line! [x-scale step]
  (let [step-start-position #(scaled-time x-scale % "start_time")]
  (-> step
      (.selectAll "line")
        (.data #(aget % "actions"))
      (.enter)
        (.append "line")
        (.attr "class" "container-step-start-line")
        (.attr "x1"    step-start-position)
        (.attr "x2"    step-start-position)
        (.attr "y1"    #(- (container-position %)
                           step-start-line-extension))
        (.attr "y2"    #(+ (container-position %)
                           container-bar-height
                           step-start-line-extension)))))

(defn draw-steps! [x-scale chart steps]
  (let [steps-group       (-> chart
                              (.append "g"))

        step              (-> steps-group
                              (.selectAll "g")
                                (.data (clj->js steps))
                              (.enter)
                                (.append "g"))]
    (draw-step-start-line! x-scale step)
    (draw-containers! x-scale step)))

(defn draw-label! [chart number-of-containers]
  (let [[x-trans y-trans] [-30 (+ (/ (timings-height number-of-containers) 2) 40)]
        rotation          -90]
  (-> chart
      (.append "text")
        (.attr "class" "y-axis-label")
        (.attr "transform" (gstring/format "translate(%d,%d) rotate(%d)" x-trans y-trans rotation))
        (.text "CONTAINERS"))))

(defn draw-axis! [chart axis class-name]
  (-> chart
      (.append "g")
        (.attr "class" class-name)
        (.call axis)))

(defn draw-chart! [{:keys [parallel steps start_time stop_time] :as build}]
  (let [x-scale (create-x-scale start_time stop_time)
        chart   (create-root-svg parallel)
        x-axis  (create-x-axis (duration start_time stop_time))
        y-axis  (create-y-axis parallel)]
    (draw-axis!  chart x-axis "x-axis")
    (draw-axis!  chart y-axis "y-axis")
    (draw-label! chart parallel)
    (draw-steps! x-scale chart steps)))

;;;; Main component
(defn build-timings [{:keys [build project plan]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:loading? true
       :drawn? false
       :resize-key (disposable/register
                     (gevents/listen js/window "resize" #(om/set-state! owner [:drawn?] false))
                     gevents/unlistenByKey)})

    om/IWillUnmount
    (will-unmount [_]
      (disposable/dispose (om/get-state owner [:resize-key])))

    om/IDidUpdate
    (did-update [_ _ _]
      (if (project-model/show-build-timing? project plan)
        (when-not (om/get-state owner [:drawn?])
          (draw-chart! build)
          (om/set-state! owner [:loading?] false)
          (om/set-state! owner [:drawn?] true))
        ((om/get-shared owner :track-event) {:event-type :build-timing-upsell-impression})))
    om/IRenderState
    (render-state [_ {:keys [loading?]}]
      (html
        [:div.build-timings
         (if (project-model/show-build-timing? project plan)
           [:div
            (when loading?
              [:div.loading-spinner common/spinner])
            [:svg#build-timings]]
           [:span.message "This release of Build Timing is only available for repos belonging to paid plans "
            [:a.upgrade-link {:href (routes/v1-org-settings-path {:org (:org_name plan) :vcs_type (:vcs_type project)})
                              :on-click #((om/get-shared owner :track-event) {:event-type :build-timing-upsell-click})}
             "upgrade here."]])]))))
