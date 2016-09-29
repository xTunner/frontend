(ns frontend.components.build-timings
  (:require [frontend.async :as f.async]
            [frontend.components.common :as common]
            [frontend.datetime :as datetime]
            [frontend.disposable :as disposable]
            [frontend.models.build :as build]
            [frontend.models.project :as project-model]
            [frontend.routes :as routes]
            [frontend.utils :as utils]
            [goog.dom :as dom]
            [goog.events :as gevents]
            [goog.string :as gstring]
            [om.core :as om :include-macros true])
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

(defn create-root-svg [dom-root number-of-containers]
  (let [root (.select js/d3 dom-root)]
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

(defn scroll-to-step [owner step]
  (let [action-id (aget step "step")]
    (when-let [action-node (dom/getElement (str "action-" action-id))]
      (utils/scroll-to-build-action! action-node)
      (f.async/raise! owner [:action-log-output-toggled
                             {:index (aget step "index")
                              :step (aget step "step")
                              :value true}])
      (om/set-state! owner :action-id-to-scroll-to nil))))

(defn infos
  [owner & args]
  (let [container-index (aget (first args) "index")
        action-id (aget (first args) "step")
        to-raise [:action-log-output-toggled
                  {:index container-index
                   :step action-id
                   :value true}]]
    (js/console.log "PRE set value in state:" (om/get-state owner [:action-id-to-scroll-to]))
    (js/console.log "raising:" to-raise)
    (om/set-state! owner :action-id-to-scroll-to action-id)
    (f.async/raise! owner to-raise)
    (js/console.log "POST set/raise value in state:" (om/get-state owner [:action-id-to-scroll-to]))))

(defn draw-containers! [owner x-scale step]
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
            (.on   "click"     (partial scroll-to-step owner))
          (.append "title")
            (.text #(gstring/format "%s (%s:%s) - %s"
                                    (aget % "name")
                                    (aget % "index")
                                    (aget % "step")
                                    (step-duration %))))))

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

(defn draw-steps! [owner x-scale chart steps]
  (let [steps-group       (-> chart
                              (.append "g"))

        step              (-> steps-group
                              (.selectAll "g")
                                (.data (clj->js steps))
                              (.enter)
                                (.append "g"))]
    (draw-step-start-line! x-scale step)
    (draw-containers! owner x-scale step)))

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

(defn draw-chart! [owner {:keys [parallel steps start_time stop_time] :as build}]
  (let [root    (om/get-node owner "build-timings-svg")
        x-scale (create-x-scale start_time stop_time)
        chart   (create-root-svg root parallel)
        x-axis  (create-x-axis (duration start_time stop_time))
        y-axis  (create-y-axis parallel)]
    (draw-axis!  chart x-axis "x-axis")
    (draw-axis!  chart y-axis "y-axis")
    (draw-label! chart parallel)
    (draw-steps! owner x-scale chart steps)))

(defn build-timings-chart [build owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (when build
        (draw-chart! owner build))
      (om/set-state! owner [:resize-key]
                     (disposable/register
                      (gevents/listen js/window "resize"
                                      #(draw-chart!
                                        owner
                                        (om/get-props owner)))
                      gevents/unlistenByKey)))

    om/IWillUnmount
    (will-unmount [_]
      (disposable/dispose (om/get-state owner [:resize-key])))

    om/IDidUpdate
    (did-update [_ _ _]
      (when build
        (draw-chart! owner build)))

    om/IRenderState
    (render-state [_ _]
      (html
       [:div
        (when-not build
          [:div.loading-spinner common/spinner])
        [:svg {:ref "build-timings-svg"}]]))))

(defn upsell-message [plan owner]
  (reify
    om/IDidMount
    (did-mount [_]
      ((om/get-shared owner :track-event) {:event-type :build-timing-upsell-impression}))

    om/IRenderState
    (render-state [_ _]
      (let [{{plan-org-name :name
              plan-vcs-type :vcs_type} :org} plan]
        (html
         [:span.message "This release of Build Timing is only available for repos belonging to paid plans. Please "
          [:a.upgrade-link
           {:href (routes/v1-org-settings-path {:org plan-org-name
                                                :vcs_type plan-vcs-type})
            :on-click #((om/get-shared owner :track-event) {:event-type :build-timing-upsell-click})}
           "upgrade here."]])))))

;;;; Main component
(defn build-timings [{:keys [build project plan]} owner]
  (reify
    om/IRenderState
    (render-state [_ _]
      (html
       [:div.build-timings
        (if (project-model/show-build-timing? project plan)
          (om/build build-timings-chart build)
          (om/build upsell-message plan))]))))
