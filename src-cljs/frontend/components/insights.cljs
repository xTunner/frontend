(ns frontend.components.insights
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as string]
            [frontend.async :refer [raise!]]
            [frontend.routes :as routes]
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
  {:width 425
   :height 100
   :top 10, :right 10, :bottom 10, :left 30})

(def plot-info
  {:width (- (:width svg-info) (:left svg-info) (:right svg-info))
   :height (- (:height svg-info) (:top svg-info) (:bottom svg-info))
   :max-bars 55
   :positive-y% 0.60})

(defn add-queued-time [build]
  (let [queued-time (max (build/queued-time build) 0)]
    (assoc build :queued_time_millis queued-time)))

(defn build-graphable [{:keys [outcome]}]
  (#{"success" "failed" "canceled"} outcome))

(defn visualize-insights-bar! [el builds owner]
  (let [[y-pos-max y-neg-max] (->> [:build_time_millis :queued_time_millis]
                                   (map #(->> builds
                                              (map %)
                                              (apply max))))
        y-zero (->> [:height :positive-y%]
                    (map plot-info)
                    (apply *))
        y-pos-scale (-> (js/d3.scale.linear)
                        (.domain #js[0 y-pos-max])
                        (.range #js[y-zero 0]))
        y-neg-scale (-> (js/d3.scale.linear)
                        (.domain #js[0 y-neg-max])
                        (.range #js[y-zero (:height plot-info)]))
        y-pos-floored-max (datetime/nice-floor-duration y-pos-max)
        y-pos-tick-values (list y-pos-floored-max 0)
        y-neg-tick-values [(datetime/nice-floor-duration y-neg-max)]
        [y-pos-axis y-neg-axis] (for [[scale tick-values] [[y-pos-scale y-pos-tick-values]
                                                           [y-neg-scale y-neg-tick-values]]]
                                  (-> (js/d3.svg.axis)
                                      (.scale scale)
                                      (.orient "left")
                                      (.tickValues (clj->js tick-values))
                                      (.tickFormat #(first (datetime/millis-to-float-duration % {:decimals 0})))
                                      (.tickSize 0 0)
                                      (.tickPadding 3)))
        scale-filler (->> (list (:max-bars plot-info) (count builds))
                          (apply -)
                          range
                          (map (partial str "xx-")))
        x-scale (-> (js/d3.scale.ordinal)
                    (.domain (clj->js
                              (concat (map :build_num builds) scale-filler)))
                    (.rangeBands #js[0 (:width plot-info)] 0.4))
        plot (-> js/d3
                (.select el)
                (.select "svg g.plot-area"))
        bars-join (-> plot
                      (.select "g > g.bars")
                      (.selectAll "g.bar-pair")
                      (.data (clj->js builds)))
        bars-enter-g (-> bars-join
                         (.enter)
                         (.append "g")
                         (.attr "class" "bar-pair"))
        grid-y-pos-vals (for [tick (remove zero? y-pos-tick-values)] (y-pos-scale tick))
        grid-y-neg-vals (for [tick (remove zero? y-neg-tick-values)] (y-neg-scale tick))
        grid-lines-join (-> plot
                            (.select "g.grid-lines")
                            (.selectAll "line.horizontal")
                            (.data (clj->js (concat grid-y-pos-vals grid-y-neg-vals))))]

    ;; top bar enter
    (-> bars-enter-g
        (.append "a")
        (.attr "class" "top")
        (.append "rect")
        (.attr "class" "bar"))

    ;; bottom (queue time) bar enter
    (-> bars-enter-g
        (.append "a")
        (.attr "class" "bottom")
        (.append "rect")
        (.attr "class" "bar bottom queue"))

    ;; top bars enter and update
    (-> bars-join
        (.select ".top")
        (.attr #js {"xlink:href" #(utils/uri-to-relative (aget % "build_url"))
                    "xlink:title" #(let [duration-str (datetime/as-duration (aget % "build_time_millis"))]
                                     (gstring/format "%s in %s"
                                                     (gstring/toTitleCase (aget % "outcome"))
                                                     duration-str))})
        (.select "rect.bar")
        (.attr #js {"class" #(str "bar " (aget % "outcome"))
                    "y" #(y-pos-scale (aget % "build_time_millis"))
                    "x" #(x-scale (aget % "build_num"))
                    "width" (.rangeBand x-scale)
                    "height" #(- y-zero (y-pos-scale (aget % "build_time_millis")))}))

    ;; bottom bar enter and update
    (-> bars-join
        (.select ".bottom")
        (.attr #js {"xlink:href" #(utils/uri-to-relative (aget % "build_url"))
                    "xlink:title" #(let [duration-str (datetime/as-duration (aget % "queued_time_millis"))]
                                     (gstring/format "Queue time %s" duration-str))})
        (.select "rect.bar")
        (.attr #js {"y" y-zero
                    "x" #(x-scale (aget % "build_num"))
                    "width" (.rangeBand x-scale)
                    "height" #(- (y-neg-scale (aget % "queued_time_millis")) y-zero)}))

    ;; bars exit
    (-> bars-join
        (.exit)
        (.remove))

    ;; y-axis
    (-> plot
        (.select ".axis-container g.y-axis.positive")
        (.call y-pos-axis))
    (-> plot
        (.select ".axis-container g.y-axis.negative")
        (.call y-neg-axis))
    ;; x-axis
    (-> plot
        (.select ".axis-container g.axis.x-axis line")
        (.attr #js {"y1" y-zero
                    "y2" y-zero
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
                      (.attr #js {"xlink" "http://www.w3.org/1999/xlink"
                                  "width" (:width svg-info)
                                  "height" (:height svg-info)})
                      (.append "g")
                      (.attr "class" "plot-area")
                      (.attr "transform" (gstring/format "translate(%s,%s)"
                                                         (:left svg-info)
                                                         (:top svg-info))))]

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
          (.attr "class" "y-axis positive axis"))
      (-> axis-container
          (.append "g")
          (.attr "class" "y-axis negative axis")))))

(defn chartable-builds [builds]
  (->> builds
       (filter build-graphable)
       reverse
       (map add-queued-time)
       (take (:max-bars plot-info))))

(defn median-builds [builds f]
  (let [nums (->> builds
                  (map f)
                  sort)
        c (count nums)
        mid-i (js/Math.floor (/ c 2))]
    (if (odd? c)
      (nth nums mid-i)
      (/ (+ (nth nums mid-i)
            (nth nums (dec mid-i)))
         2))))

(defn project-insights-bar [builds owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [el (om/get-node owner)]
        (insert-skeleton el)
        (visualize-insights-bar! el builds owner)))
    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      (let [el (om/get-node owner)]
        (visualize-insights-bar! el builds owner)))
    om/IRender
    (render [_]
      (html
       [:div.build-time-visualization]))))

(defrender project-insights [{:keys [reponame username branches recent-builds] :as project} owner]
  (let [builds (chartable-builds recent-builds)]
    (html
     (let [branch (-> recent-builds (first) (:branch))]
       [:div.project-block
        [:h1 (gstring/format "%s/%s" username reponame)]
        [:h4 "Branch: " branch]
        (cond (nil? recent-builds) [:div.loading-spinner common/spinner]
              (empty? builds) [:div.no-builds "No builds."]
              :else
              (list
               [:div.above-info
                [:dl
                 [:dt "MEDIAN BUILD"]
                 [:dd (datetime/as-duration (median-builds builds :build_time_millis))]]
                [:dl
                 [:dt "MEDIAN QUEUE"]
                 [:dd (datetime/as-duration (median-builds builds :queued_time_millis))]]
                [:dl
                 [:dt "LAST BUILD"]
                 [:dd (datetime/as-time-since (-> builds last :start_time))]]]
               (om/build project-insights-bar builds)
               [:div.below-info
                [:dl
                 [:dt "Branches:"]
                 [:dd (-> branches keys count)]]]))]))))

(defrender no-projects [data owner]
  (html
    [:div.no-insights-block
     [:div.content
      [:div.row
       [:div.header.text-center "No Insights yet"]]
       [:div.details.text-center "Add projects from your Github orgs and start building on CircleCI to view insights."]
      [:div.row.text-center
       [:a.btn.btn-success {:href (routes/v1-add-projects)} "Add Project"]]]]))

(defrender build-insights [state owner]
  (let [projects (get-in state state/projects-path)]
    (html
     [:div#build-insights {:class (case (count projects)
                                    1 "one-project"
                                    2 "two-projects"
                                    "three-or-more-projects")}
        [:header.main-head
         [:div.head-user
          [:h1 "Insights » Repositories"]]]
        (cond
          (nil? projects)    [:div.loading-spinner-big common/spinner]
          (empty? projects)  (om/build no-projects state)
          :else              (om/build-all project-insights projects))])))
