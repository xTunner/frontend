(ns frontend.components.insights
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [cljs.core.match :refer-macros [match]]
            [clojure.string :as string]
            [frontend.async :refer [raise!]]
            [frontend.analytics :as analytics]
            [frontend.routes :as routes]
            [frontend.components.common :as common]
            [frontend.components.forms :refer [managed-button]]
            [frontend.components.svg :refer [svg]]
            [frontend.config :as config]
            [frontend.datetime :as datetime]
            [frontend.models.project :as project-model]
            [frontend.models.repo :as repo-model]
            [frontend.models.user :as user-model]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [inspect] :refer [unexterned-prop]]
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

(defn build-chartable? [{:keys [outcome build_time_millis]}]
  (or (#{"success" "failed"} outcome)
      (and (= "canceled" outcome)
           build_time_millis)))

(defn build-timing-url [build]
  (str (utils/uri-to-relative (unexterned-prop build "build_url"))
       "#build-timing"))

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
        (.attr #js {"xlink:href" build-timing-url
                    "xlink:title" #(let [duration-str (datetime/as-duration (unexterned-prop % "build_time_millis"))]
                                     (gstring/format "%s in %s"
                                                     (gstring/toTitleCase (unexterned-prop % "outcome"))
                                                     duration-str))})
        (.select "rect.bar")
        (.attr #js {"class" #(str "bar " (unexterned-prop % "outcome"))
                    "y" #(y-pos-scale (unexterned-prop % "build_time_millis"))
                    "x" #(x-scale (unexterned-prop % "build_num"))
                    "width" (.rangeBand x-scale)
                    "height" #(- y-zero (y-pos-scale (unexterned-prop % "build_time_millis")))}))

    ;; bottom bar enter and update
    (-> bars-join
        (.select ".bottom")
        (.attr #js {"xlink:href" build-timing-url
                    "xlink:title" #(let [duration-str (datetime/as-duration (unexterned-prop % "queued_time_millis"))]
                                     (gstring/format "Queue time %s" duration-str))})
        (.select "rect.bar")
        (.attr #js {"y" y-zero
                    "x" #(x-scale (unexterned-prop % "build_num"))
                    "width" (.rangeBand x-scale)
                    "height" #(- (y-neg-scale (unexterned-prop % "queued_time_millis")) y-zero)}))

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

(defn filter-chartable-builds [builds]
  (->> builds
       (filter build-chartable?)
       (take (:max-bars plot-info))
       reverse
       (map add-queued-time)))

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

(defn formatted-project-name [{:keys [username reponame]}]
  (gstring/format "%s/%s" username reponame))

(defn project-insights [{:keys [show-insights? reponame username branches recent-builds chartable-builds sort-category parallel] :as project} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (when (not show-insights?)
        (analytics/track-build-insights-upsell-impression {:reponame reponame
                                                           :org-name username})))
    om/IRender
    (render [_]
      (html
       (let [branch (-> recent-builds (first) (:branch))
             latest-build (last chartable-builds)]
         [:div.project-block {:class (str "build-" (name sort-category))}
          [:h1.project-header
           [:div.last-build-status
            (om/build svg {:class "badge-icon"
                           :src (-> latest-build build/status-icon common/icon-path)})]
           [:span.project-name (formatted-project-name project)]
           [:div.github-icon
            [:a {:href (:vcs_url project)}
             [:i.octicon.octicon-mark-github]]]
           [:div.settings-icon
            [:a {:href (routes/v1-project-settings {:org username
                                                    :repo reponame})}
             [:i.material-icons "settings"]]]]
          [:h4 (if show-insights?
                 (str "Branch: " branch)
                 (gstring/unescapeEntities "&nbsp;"))]
          (cond (nil? recent-builds) [:div.loading-spinner common/spinner]
                (not show-insights?) [:div.no-insights
                                      [:div.message "This release of Insights is only available for repos belonging to paid plans."]
                                      [:a.upgrade-link {:href (routes/v1-org-settings {:org (vcs-url/org-name (:vcs_url project))})
                                                        :on-click #(analytics/track-build-insights-upsell-click {:reponame reponame
                                                                                                                 :org-name username})} "Upgrade here"]]
                (empty? chartable-builds) [:div.no-builds "No tests for this repo"]
                :else
                (list
                 [:div.above-info
                  [:dl
                   [:dt "MEDIAN BUILD"]
                   [:dd (datetime/as-duration (median-builds chartable-builds :build_time_millis))]]
                  [:dl
                   [:dt "MEDIAN QUEUE"]
                   [:dd (datetime/as-duration (median-builds chartable-builds :queued_time_millis))]]
                  [:dl
                   [:dt "LAST BUILD"]
                   [:dd (om/build common/updating-duration
                                  {:start (->> chartable-builds
                                               reverse
                                               (filter :start_time)
                                               first
                                               :start_time)}
                                  {:opts {:formatter datetime/as-time-since
                                          :formatter-use-start? true}})]]]
                 (om/build project-insights-bar chartable-builds)
                 [:div.below-info
                  [:dl
                   [:dt "BRANCHES"]
                   [:dd (-> branches keys count)]]
                  [:dl
                   [:dt "PARALLELISM"]
                   [:dd parallel]]]))])))))

(defrender no-projects [data owner]
  (html
   [:div.no-projects-block
    [:div.content
     [:div.row
      [:div.header.text-center "No Insights yet"]]
     [:div.details.text-center "Add projects from your Github orgs and start building on CircleCI to view insights."]
     [:div.row.text-center
      [:a.btn.btn-success {:href (routes/v1-add-projects)} "Add Project"]]]]))

(defn project-sort-category
  "Returns symbol representing category for sorting.

  One of #{:pass :fail :other}"
  [{:keys [show-insights? chartable-builds] :as project}]
  (let [outcome (some->> chartable-builds
                         (map :outcome)
                         (filter #{"success" "failed"})
                         last)]
    (match [show-insights? outcome]
           [true "success"] :success
           [true "failed"] :failed
           :else :other)))

(defn project-latest-build-time [project]
  (let [start-time (-> project
                       :chartable-builds
                       last
                       :start_time)]
    (js/Date. start-time)))

(defn decorate-project
  "Add keys to project related to insights - :show-insights? :sort-category :chartable-builds ."
  [plans {:keys [recent-builds] :as project}]
  (let [chartable-builds (filter-chartable-builds recent-builds)]
    (-> project
        (assoc :chartable-builds chartable-builds)
        (#(assoc % :show-insights? (project-model/show-insights? plans %)))
        (#(assoc % :sort-category (project-sort-category %)))
        (#(assoc % :latest-build-time (project-latest-build-time %))))))

(defrender cards [{:keys [plans projects selected-filter selected-sorting]} owner]
  (let [decorated-projects (map (partial decorate-project plans) projects)
        categories (group-by :sort-category decorated-projects)
        filtered-projects (if (= selected-filter :all)
                            decorated-projects
                            (selected-filter categories))
        sorted-projects (case selected-sorting
                          :alphabetical (->> filtered-projects
                                             (sort-by #(-> %
                                                           formatted-project-name
                                                           ((juxt string/lower-case identity)))))
                          :recency (->> filtered-projects
                                        (sort-by :latest-build-time)
                                        reverse))]
    (html
     [:div
      [:div.controls
       [:span.filtering
        [:input {:id "insights-filter-all"
                 :type "radio"
                 :name "selected-filter"
                 :checked (= selected-filter :all)
                 :on-change #(raise! owner [:insights-filter-changed {:new-filter :all}])}]
        [:label {:for "insights-filter-all"}
         (gstring/format"All (%s)" (count decorated-projects))]
        [:input {:id "insights-filter-success"
                 :type "radio"
                 :name "selected-filter"
                 :checked (= selected-filter :success)
                 :on-change #(raise! owner [:insights-filter-changed {:new-filter :success}])}]
        [:label {:for "insights-filter-success"}
         (gstring/format"Successful (%s)" (count (:success categories)))]
        [:input {:id "insights-filter-failed"
                 :type "radio"
                 :name "selected-filter"
                 :checked (= selected-filter :failed)
                 :on-change #(raise! owner [:insights-filter-changed {:new-filter :failed}])}]
        [:label {:for "insights-filter-failed"}
         (gstring/format"Failed (%s)" (count (:failed categories)))]]
       [:span.sorting
        [:label "Sort: "]
        [:select {:class "toggle-sorting"
                  :on-change #(raise! owner [:insights-sorting-changed {:new-sorting (keyword (.. % -target -value))}])
                  :value (name selected-sorting)}
         [:option {:value "alphabetical"} "Alphabetical"]
         [:option {:value "recency"} "Recent"]]]]
      [:div.blocks-container
       (om/build-all project-insights sorted-projects)]])))

(defrender build-insights [state owner]
  (let [projects (get-in state state/projects-path)]
    (html
     [:div#build-insights {}
      (cond
        (nil? projects)    [:div.loading-spinner-big common/spinner]
        (empty? projects)  (om/build no-projects state)
        :else              (om/build cards {:plans (get-in state state/user-plans-path)
                                            :projects (get-in state state/projects-path)
                                            :selected-filter (get-in state state/insights-filter-path)
                                            :selected-sorting (get-in state state/insights-sorting-path)}))])))
