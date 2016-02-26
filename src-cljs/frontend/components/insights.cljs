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
            [frontend.models.build :as build]
            [frontend.models.project :as project-model]
            [frontend.models.repo :as repo-model]
            [frontend.models.user :as user-model]
            [frontend.models.feature :as feature]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [inspect] :refer [unexterned-prop]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.routes :as routes]
            [goog.string :as gstring]
            [goog.string.format]
            [goog.events :as gevents]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [schema.core :as s :include-macros true]
            [devcards.core :as dc :refer-macros [defcard]])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [html defrender]]))

(def BarChartableBuild
  {:build_num s/Int
   :start_time (s/pred #(re-matches #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\d.\d\d\dZ" %))
   :build_time_millis s/Int
   :queued_time_millis s/Int
   :outcome s/Str
   s/Any s/Any})

(def default-plot-info
  {:top 10
   :right 10
   :bottom 10
   :left 30
   :max-bars 55
   :positive-y% 0.6})

(defn add-legend [plot-info svg]
  (let [{:keys [square-size item-width item-height spacing]} (:legend-info plot-info)
        left-legend-enter (-> svg
                              (.select ".legend-container")
                              (.append "svg")
                              (.attr #js {"x" 0
                                          "y" 0
                                          "class" "left-legend-container"})
                              (.style #js {"overflow" "visible"})
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
                               (.append "svg")
                               (.attr #js {"x" "90%"
                                           "y" 0
                                           "class" "right-legend-container"})
                               (.style #js {"overflow" "visible"})
                               (.selectAll ".legend")
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

(defn visualize-insights-bar! [plot-info el builds {:keys [on-focus-build on-mouse-move]
                                                    :or {on-focus-build (constantly nil)
                                                         on-mouse-move (constantly nil)}
                                                    :as events} owner]
  (let [[y-pos-max y-neg-max] (->> [:build_time_millis :queued_time_millis]
                                   (map #(->> builds
                                              (map %)
                                              (apply max))))
        svg (-> js/d3
                (.select el)
                (.select "svg")
                ;; Set the SVG up to redraw itself when it resizes.
                (.property "redraw-fn" (constantly #(visualize-insights-bar! plot-info el builds events owner)))
                (.on "mousemove" #(on-mouse-move (d3.mouse el))))
        svg-bounds (-> svg
                       ffirst
                       .getBoundingClientRect)

        ;; The width and height of the area we'll draw the bars in. (Excludes,
        ;; for instance, the Y-axis labels on the left.)
        width (- (.-width svg-bounds) (:left plot-info) (:right plot-info))
        height (- (.-height svg-bounds) (:top plot-info) (:bottom plot-info))

        y-zero (* height (:positive-y% plot-info))
        y-pos-scale (-> (js/d3.scale.linear)
                        (.domain #js[0 y-pos-max])
                        (.range #js[y-zero 0]))
        y-neg-scale (-> (js/d3.scale.linear)
                        (.domain #js[0 y-neg-max])
                        (.range #js[y-zero height]))
        y-pos-floored-max (datetime/nice-floor-duration y-pos-max)
        y-pos-tick-values [y-pos-floored-max 0]
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
        scale-filler (->> (- (:max-bars plot-info) (count builds))
                          range
                          (map (partial str "xx-")))
        x-scale (-> (js/d3.scale.ordinal)
                    (.domain (clj->js
                              (concat scale-filler (map :build_num builds))))
                    (.rangeBands #js[0 width] 0.4))
        plot (-> svg
                 (.select "g.plot-area"))
        bars-join (-> plot
                      (.select "g > g.bars")
                      (.selectAll "g.bar-pair")
                      (.data (clj->js builds))
                      (.on "mouseover" #(on-focus-build %))
                      (.on "mousemove" #(on-focus-build %)))
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
        (.attr #js {"xlink:href" build-timing-url})
        (.on #js {"click" #(analytics/track {:event-type :insights-bar-clicked 
                                             :owner owner
                                             :properties {:build-url (unexterned-prop % "build_url")}})})
        (.select "rect.bar")
        (.attr #js {"class" #(str "bar " (unexterned-prop % "outcome"))
                    "y" #(y-pos-scale (unexterned-prop % "build_time_millis"))
                    "x" #(x-scale (unexterned-prop % "build_num"))
                    "width" (.rangeBand x-scale)
                    "height" #(- y-zero (y-pos-scale (unexterned-prop % "build_time_millis")))}))

    ;; bottom bar enter and update
    (-> bars-join
        (.select ".bottom")
        (.attr #js {"xlink:href" build-timing-url})
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
                    "x2" width}))

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
                    "x2" width}))))

(defn insert-skeleton [plot-info el]
  (let [svg (-> js/d3
                (.select el)
                (.append "svg"))
        plot-area (-> svg
                      (.attr #js {"xlink" "http://www.w3.org/1999/xlink"})
                      (.append "g")
                      (.attr "class" "plot-area")
                      (.attr "transform" (gstring/format "translate(%s,%s)"
                                                         (:left plot-info)
                                                         (:top plot-info))))]

    ;; Call the svg's "redraw-fn" (if set) whenever the svg resizes.
    ;;
    ;; There's no reliable way to get the svg to fire an event when it resizes.
    ;; There's an SVGResize event that sometimes works, and a resize event that
    ;; sometimes works, and an onresize attribute that sometimes works. The one
    ;; thing that works consistently is adding an invisible iframe pinned to the
    ;; size of the svg and listening to *its* resize event.
    (-> svg
        (.append "foreignObject")
        (.attr #js {:width "100%"
                    :height "100%"})
        (.style #js {:visibility "hidden"})
        (.append "xhtml:iframe")
        (.attr #js {:width "100%"
                    :height "100%"})
        ffirst
        .-contentWindow
        (gevents/listen "resize" #(when-let [redraw-fn (.property svg "redraw-fn")] (redraw-fn))))

    (when-let [legend-info (:legend-info plot-info)]
      (-> svg
          (.append "g")
          (.attr #js {"class" "legend-container"
                      "transform" (gstring/format "translate(%s,%s)"
                                                  (:left plot-info)
                                                  (:top legend-info))}))
      (add-legend plot-info svg))

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

(defn filter-chartable-builds [builds max-count]
  (some->> builds
           (filter build-chartable?)
           (take max-count)
           reverse
           (map add-queued-time)))

(defn median [xs]
  (let [nums (sort xs)
        c (count nums)
        mid-i (js/Math.floor (/ c 2))]
    (cond
      (zero? c) nil
      (odd? c) (nth nums mid-i)
      :else (/ (+ (nth nums mid-i)
                  (nth nums (dec mid-i)))
               2))))


(s/defn build-status-bar-chart [{:keys [plot-info builds :- [BarChartableBuild]] :as params} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [el (om/get-node owner)]
        (insert-skeleton plot-info el)
        (visualize-insights-bar! plot-info el builds (select-keys params [:on-focus-build :on-mouse-move]) owner)))
    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      (let [el (om/get-node owner)]
        (visualize-insights-bar! plot-info el builds (select-keys params [:on-focus-build :on-mouse-move]) owner)))
    om/IRender
    (render [_]
      (html
       [:div {:data-component (str `build-status-bar-chart)}]))))

(defn formatted-project-name [{:keys [username reponame]}]
  (gstring/format "%s/%s" username reponame))

(defn project-insights [{:keys [show-insights? reponame username branches recent-builds chartable-builds sort-category parallel default_branch vcs_type] :as project} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (when (not show-insights?)
        (analytics/track {:event-type :build-insights-upsell-impression
                          :owner owner
                          :properties {:repo (project-model/repo-name project)
                                       :org (project-model/org-name project)}})))
    om/IRender
    (render [_]
      (html
        (let [branch default_branch
              latest-build (last chartable-builds)
              org-name (project-model/org-name project)
              repo-name (project-model/repo-name project)]
          [:div.project-block {:class (str "build-" (name sort-category))}
           [:h1.project-header
            [:div.last-build-status
             (om/build svg {:class "badge-icon"
                            :src (-> latest-build build/status-icon common/icon-path)})]
            [:span.project-name
             (if (and (feature/enabled? :insights-dashboard)
                      show-insights?)
               [:a {:href (routes/v1-insights-project-path {:org org-name
                                                            :repo repo-name
                                                            :branch (:default_branch project)
                                                            :vcs_type (:vcs_type project)})}
                (formatted-project-name project)]
               (formatted-project-name project))]
            [:div.github-icon
             [:a {:href (:vcs_url project)}
              [:i.octicon.octicon-mark-github]]]
            [:div.settings-icon
             [:a {:href (routes/v1-project-settings-path {:org username
                                                          :repo reponame
                                                          :vcs_type vcs_type})}
              [:i.material-icons "settings"]]]]
           [:h4 (if show-insights?
                  (str "Branch: " branch)
                  (gstring/unescapeEntities "&nbsp;"))]
           (cond (nil? (get recent-builds default_branch)) [:div.loading-spinner common/spinner]
                 (not show-insights?) [:div.no-insights
                                       [:div.message "This release of Insights is only available for repos belonging to paid plans."]
                                       [:a.upgrade-link {:href (routes/v1-org-settings-path {:org (vcs-url/org-name (:vcs_url project))
                                                                                             :vcs_type (:vcs_type project)})
                                                         :on-click #(analytics/track {:event-type :build-insights-upsell-click
                                                                                      :owner owner
                                                                                      :properties  {:repo repo-name
                                                                                                    :org org-name}})} "Upgrade here"]]
                (empty? chartable-builds) [:div.no-builds "No tests for this repo"]
                :else
                (list
                 [:div.above-info
                  [:dl
                   [:dt "median build"]
                   [:dd (datetime/as-duration (median (map :build_time_millis chartable-builds))) " min"]]
                  [:dl
                   [:dt "median queue"]
                   [:dd (datetime/as-duration (median (map :queued_time_millis chartable-builds))) " min"]]
                  [:dl
                   [:dt "last build"]
                   [:dd (om/build common/updating-duration
                                  {:start (->> chartable-builds
                                               reverse
                                               (filter :start_time)
                                               first
                                               :start_time)}
                                  {:opts {:formatter datetime/as-time-since
                                          :formatter-use-start? true}})]]]
                 [:div.body-info
                  (om/build build-status-bar-chart {:plot-info default-plot-info
                                                    :builds chartable-builds})]
                 [:div.below-info
                  [:dl
                   [:dt "parallelism"]
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
  [{:keys [max-bars] :as plot-info} plans {:keys [recent-builds default_branch] :as project}]
  (let [chartable-builds (filter-chartable-builds (get recent-builds default_branch)
                                                  max-bars)]
    (-> project
        (assoc :chartable-builds chartable-builds)
        (#(assoc % :show-insights? (project-model/show-insights? plans %)))
        (#(assoc % :sort-category (project-sort-category %)))
        (#(assoc % :latest-build-time (project-latest-build-time %))))))

(defrender cards [{:keys [projects selected-filter selected-sorting]} owner]
  (let [categories (-> (group-by :sort-category projects)
                       (assoc :all projects))
        filtered-projects (selected-filter categories)
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
        (for [[filter-name filter-label] [[:all "All"]
                                          [:success "Success"]
                                          [:failed "Failed"]]]
          (let [filter-input-id (str "insights-filter-" (name filter-name))]
            (list
             [:input {:id filter-input-id
                      :type "radio"
                      :name "selected-filter"
                      :checked (= selected-filter filter-name)
                      :on-change #(raise! owner [:insights-filter-changed {:new-filter filter-name}])}]
             [:label {:for filter-input-id}
              (gstring/format "%s (%s)" filter-label (count (filter-name categories)))])))]
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
  (let [projects (get-in state state/projects-path)
        plans (get-in state state/user-plans-path)
        navigation-data (:navigation-data state)
        decorate (partial decorate-project default-plot-info plans)]
    (html
     [:div#build-insights
      (cond
        ;; Still loading projects
        (nil? projects)
        [:div.loading-spinner-big common/spinner]

        ;; User has no projects
        (empty? projects)
        (om/build no-projects state)

        ;; User is looking at all projects
        :else
        (om/build cards {:projects (map decorate projects)
                         :selected-filter (get-in state state/insights-filter-path)
                         :selected-sorting (get-in state state/insights-sorting-path)}))])))


(when config/client-dev?
  ;; TODO: Auto-generate build data (perhaps with Schema + test.check).
  (def some-builds
    [{:build_num 136387, :start_time "2016-02-03T18:32:47.650Z", :build_time_millis 1440091, :queued_time_millis 0, :outcome "success"} {:build_num 136402, :start_time "2016-02-03T22:07:24.437Z", :build_time_millis 2512182, :queued_time_millis 0, :outcome "success"} {:build_num 136408, :start_time "2016-02-04T00:10:18.624Z", :build_time_millis 1350720, :queued_time_millis 0, :outcome "success"} {:build_num 136416, :start_time "2016-02-04T01:46:39.151Z", :build_time_millis 2408743, :queued_time_millis 6260, :outcome "success"} {:build_num 136428, :start_time "2016-02-04T07:03:09.156Z", :build_time_millis 782560, :queued_time_millis 0, :outcome "failed"} {:build_num 136430, :start_time "2016-02-04T07:19:58.545Z", :build_time_millis 1345931, :queued_time_millis 0, :outcome "success"} {:build_num 136438, :start_time "2016-02-04T09:05:04.913Z", :build_time_millis 1758123, :queued_time_millis 0, :outcome "success"} {:build_num 136443, :start_time "2016-02-04T10:12:21.666Z", :build_time_millis 1436773, :queued_time_millis 2039, :outcome "success"} {:build_num 136448, :start_time "2016-02-04T10:37:12.887Z", :build_time_millis 2261798, :queued_time_millis 5550, :outcome "success"} {:build_num 136454, :start_time "2016-02-04T11:03:41.223Z", :build_time_millis 1038578, :queued_time_millis 536, :outcome "failed"} {:build_num 136455, :start_time "2016-02-04T11:24:46.375Z", :build_time_millis 2149304, :queued_time_millis 0, :outcome "success"} {:build_num 136459, :start_time "2016-02-04T11:41:05.936Z", :build_time_millis 1456182, :queued_time_millis 0, :outcome "success"} {:build_num 136463, :start_time "2016-02-04T14:03:33.271Z", :build_time_millis 614985, :queued_time_millis 0, :outcome "failed"} {:build_num 136465, :start_time "2016-02-04T14:16:10.803Z", :build_time_millis 701386, :queued_time_millis 388, :outcome "failed"} {:build_num 136466, :start_time "2016-02-04T14:16:56.349Z", :build_time_millis 1327913, :queued_time_millis 0, :outcome "success"} {:build_num 136467, :start_time "2016-02-04T14:23:05.392Z", :build_time_millis 1442550, :queued_time_millis 0, :outcome "success"} {:build_num 136471, :start_time "2016-02-04T14:40:51.772Z", :build_time_millis 2792335, :queued_time_millis 6216, :outcome "success"} {:build_num 136472, :start_time "2016-02-04T15:19:15.650Z", :build_time_millis 1334421, :queued_time_millis 7424, :outcome "success"} {:build_num 136483, :start_time "2016-02-04T17:46:47.650Z", :build_time_millis 2308508, :queued_time_millis 9785, :outcome "success"} {:build_num 136487, :start_time "2016-02-04T19:31:21.414Z", :build_time_millis 2213050, :queued_time_millis 0, :outcome "success"} {:build_num 136489, :start_time "2016-02-04T19:44:00.939Z", :build_time_millis 2278400, :queued_time_millis 15842, :outcome "success"} {:build_num 136492, :start_time "2016-02-04T20:22:38.741Z", :build_time_millis 605135, :queued_time_millis 0, :outcome "failed"} {:build_num 136493, :start_time "2016-02-04T20:46:53.241Z", :build_time_millis 1482786, :queued_time_millis 0, :outcome "success"} {:build_num 136494, :start_time "2016-02-04T20:55:01.408Z", :build_time_millis 1478069, :queued_time_millis 0, :outcome "success"} {:build_num 136500, :start_time "2016-02-04T21:18:44.939Z", :build_time_millis 1366314, :queued_time_millis 0, :outcome "success"} {:build_num 136501, :start_time "2016-02-04T21:22:16.081Z", :build_time_millis 2383971, :queued_time_millis 3143, :outcome "success"} {:build_num 136508, :start_time "2016-02-04T23:01:10.542Z", :build_time_millis 1499119, :queued_time_millis 2453, :outcome "success"} {:build_num 136511, :start_time "2016-02-04T23:13:54.608Z", :build_time_millis 2353609, :queued_time_millis 0, :outcome "success"} {:build_num 136515, :start_time "2016-02-05T00:05:43.286Z", :build_time_millis 1519625, :queued_time_millis 1533, :outcome "success"} {:build_num 136542, :start_time "2016-02-05T11:14:05.785Z", :build_time_millis 821522, :queued_time_millis 94, :outcome "failed"} {:build_num 136544, :start_time "2016-02-05T11:39:37.596Z", :build_time_millis 1449719, :queued_time_millis 342, :outcome "success"} {:build_num 136547, :start_time "2016-02-05T13:15:00.536Z", :build_time_millis 1364707, :queued_time_millis 2607, :outcome "success"} {:build_num 136555, :start_time "2016-02-05T18:25:14.524Z", :build_time_millis 1380441, :queued_time_millis 538, :outcome "success"} {:build_num 136561, :start_time "2016-02-05T19:32:52.989Z", :build_time_millis 2225624, :queued_time_millis 0, :outcome "success"} {:build_num 136568, :start_time "2016-02-05T21:26:49.776Z", :build_time_millis 2274118, :queued_time_millis 0, :outcome "success"} {:build_num 136577, :start_time "2016-02-05T22:44:33.495Z", :build_time_millis 1333993, :queued_time_millis 116, :outcome "success"} {:build_num 136588, :start_time "2016-02-06T00:10:13.644Z", :build_time_millis 1575458, :queued_time_millis 1048, :outcome "failed"} {:build_num 136591, :start_time "2016-02-06T01:01:40.468Z", :build_time_millis 2638465, :queued_time_millis 1876, :outcome "success"} {:build_num 136594, :start_time "2016-02-06T03:30:44.907Z", :build_time_millis 549724, :queued_time_millis 1377, :outcome "failed"} {:build_num 136595, :start_time "2016-02-06T20:37:20.041Z", :build_time_millis 1277695, :queued_time_millis 1991, :outcome "success"} {:build_num 136605, :start_time "2016-02-07T21:38:33.162Z", :build_time_millis 1369645, :queued_time_millis 3028, :outcome "success"} {:build_num 136607, :start_time "2016-02-08T01:06:23.541Z", :build_time_millis 1483677, :queued_time_millis 0, :outcome "success"} {:build_num 136609, :start_time "2016-02-08T07:58:32.090Z", :build_time_millis 2216233, :queued_time_millis 878, :outcome "success"} {:build_num 136613, :start_time "2016-02-08T13:10:01.124Z", :build_time_millis 26495, :queued_time_millis 0, :outcome "canceled"} {:build_num 136615, :start_time "2016-02-08T13:10:31.057Z", :build_time_millis 34021, :queued_time_millis 569, :outcome "canceled"} {:build_num 136616, :start_time "2016-02-08T13:12:10.087Z", :build_time_millis 2238584, :queued_time_millis 0, :outcome "success"} {:build_num 136622, :start_time "2016-02-08T14:56:02.751Z", :build_time_millis 570396, :queued_time_millis 1302, :outcome "failed"} {:build_num 136625, :start_time "2016-02-08T17:27:41.909Z", :build_time_millis 1567314, :queued_time_millis 0, :outcome "success"} {:build_num 136638, :start_time "2016-02-08T18:43:53.544Z", :build_time_millis 2514016, :queued_time_millis 0, :outcome "success"} {:build_num 136646, :start_time "2016-02-08T19:12:27.630Z", :build_time_millis 619856, :queued_time_millis 0, :outcome "failed"} {:build_num 136652, :start_time "2016-02-08T20:26:06.027Z", :build_time_millis 2283665, :queued_time_millis 0, :outcome "success"} {:build_num 136653, :start_time "2016-02-08T20:37:57.118Z", :build_time_millis 2223032, :queued_time_millis 0, :outcome "success"} {:build_num 136658, :start_time "2016-02-08T21:12:37.182Z", :build_time_millis 1555312, :queued_time_millis 4589, :outcome "success"} {:build_num 136659, :start_time "2016-02-08T21:13:47.924Z", :build_time_millis 1586973, :queued_time_millis 5671, :outcome "success"} {:build_num 136668, :start_time "2016-02-08T22:32:07.831Z", :build_time_millis 2411158, :queued_time_millis 2084, :outcome "success"} {:build_num 136673, :start_time "2016-02-09T00:03:33.778Z", :build_time_millis 1429288, :queued_time_millis 0, :outcome "success"} {:build_num 136683, :start_time "2016-02-09T10:12:43.440Z", :build_time_millis 595483, :queued_time_millis 6669, :outcome "failed"} {:build_num 136685, :start_time "2016-02-09T11:54:34.511Z", :build_time_millis 571798, :queued_time_millis 0, :outcome "failed"} {:build_num 136686, :start_time "2016-02-09T11:58:14.115Z", :build_time_millis 767901, :queued_time_millis 0, :outcome "failed"} {:build_num 136691, :start_time "2016-02-09T13:57:13.674Z", :build_time_millis 750969, :queued_time_millis 5182, :outcome "failed"} {:build_num 136710, :start_time "2016-02-09T17:50:53.982Z", :build_time_millis 655806, :queued_time_millis 0, :outcome "failed"} {:build_num 136711, :start_time "2016-02-09T18:00:08.725Z", :build_time_millis 741390, :queued_time_millis 7825, :outcome "failed"} {:build_num 136713, :start_time "2016-02-09T18:20:43.342Z", :build_time_millis 707376, :queued_time_millis 0, :outcome "failed"} {:build_num 136715, :start_time "2016-02-09T19:04:45.744Z", :build_time_millis 588979, :queued_time_millis 0, :outcome "failed"} {:build_num 136719, :start_time "2016-02-09T19:45:45.230Z", :build_time_millis 1528867, :queued_time_millis 0, :outcome "success"} {:build_num 136729, :start_time "2016-02-09T21:13:52.521Z", :build_time_millis 1400824, :queued_time_millis 2144, :outcome "success"} {:build_num 136730, :start_time "2016-02-09T21:14:07.542Z", :build_time_millis 1348176, :queued_time_millis 0, :outcome "success"} {:build_num 136732, :start_time "2016-02-09T21:41:47.164Z", :build_time_millis 2282992, :queued_time_millis 0, :outcome "success"} {:build_num 136735, :start_time "2016-02-09T22:05:06.923Z", :build_time_millis 2410099, :queued_time_millis 1609, :outcome "success"} {:build_num 136736, :start_time "2016-02-09T22:12:47.078Z", :build_time_millis 1393451, :queued_time_millis 3188, :outcome "success"} {:build_num 136747, :start_time "2016-02-10T01:14:36.812Z", :build_time_millis 2593790, :queued_time_millis 5860, :outcome "failed"} {:build_num 136763, :start_time "2016-02-10T06:36:09.889Z", :build_time_millis 2000583, :queued_time_millis 0, :outcome "success"} {:build_num 136766, :start_time "2016-02-10T09:45:26.780Z", :build_time_millis 2304316, :queued_time_millis 0, :outcome "success"} {:build_num 136768, :start_time "2016-02-10T10:28:48.413Z", :build_time_millis 1453009, :queued_time_millis 1367, :outcome "success"} {:build_num 136774, :start_time "2016-02-10T11:57:41.117Z", :build_time_millis 757174, :queued_time_millis 0, :outcome "failed"} {:build_num 136776, :start_time "2016-02-10T14:16:33.297Z", :build_time_millis 2375650, :queued_time_millis 0, :outcome "success"} {:build_num 136780, :start_time "2016-02-10T14:54:55.685Z", :build_time_millis 2245985, :queued_time_millis 0, :outcome "success"} {:build_num 136790, :start_time "2016-02-10T18:20:25.068Z", :build_time_millis 2242146, :queued_time_millis 0, :outcome "success"} {:build_num 136791, :start_time "2016-02-10T18:20:44.328Z", :build_time_millis 542344, :queued_time_millis 0, :outcome "failed"} {:build_num 136795, :start_time "2016-02-10T18:49:24.316Z", :build_time_millis 1552550, :queued_time_millis 7399, :outcome "success"} {:build_num 136799, :start_time "2016-02-10T19:50:38.170Z", :build_time_millis 2349278, :queued_time_millis 0, :outcome "success"} {:build_num 136806, :start_time "2016-02-10T20:26:56.758Z", :build_time_millis 549855, :queued_time_millis 5632, :outcome "failed"} {:build_num 136815, :start_time "2016-02-10T21:50:51.573Z", :build_time_millis 1460915, :queued_time_millis 387, :outcome "success"} {:build_num 136818, :start_time "2016-02-10T21:59:36.952Z", :build_time_millis 572433, :queued_time_millis 2746, :outcome "failed"} {:build_num 136838, :start_time "2016-02-10T23:47:48.011Z", :build_time_millis 1549355, :queued_time_millis 3767, :outcome "success"} {:build_num 136843, :start_time "2016-02-11T00:12:27.757Z", :build_time_millis 840106, :queued_time_millis 0, :outcome "failed"} {:build_num 136852, :start_time "2016-02-11T01:04:28.936Z", :build_time_millis 846650, :queued_time_millis 5603, :outcome "failed"} {:build_num 136855, :start_time "2016-02-11T01:33:05.906Z", :build_time_millis 627238, :queued_time_millis 0, :outcome "failed"} {:build_num 136857, :start_time "2016-02-11T02:37:35.634Z", :build_time_millis 560272, :queued_time_millis 0, :outcome "failed"} {:build_num 136858, :start_time "2016-02-11T02:46:32.517Z", :build_time_millis 753217, :queued_time_millis 0, :outcome "failed"} {:build_num 136859, :start_time "2016-02-11T03:06:52.372Z", :build_time_millis 830567, :queued_time_millis 0, :outcome "failed"} {:build_num 136863, :start_time "2016-02-11T06:50:01.957Z", :build_time_millis 801412, :queued_time_millis 0, :outcome "failed"} {:build_num 136865, :start_time "2016-02-11T07:27:45.797Z", :build_time_millis 746620, :queued_time_millis 11396, :outcome "failed"} {:build_num 136867, :start_time "2016-02-11T07:48:22.626Z", :build_time_millis 1417994, :queued_time_millis 0, :outcome "success"} {:build_num 136870, :start_time "2016-02-11T08:44:40.215Z", :build_time_millis 1374758, :queued_time_millis 0, :outcome "success"} {:build_num 136872, :start_time "2016-02-11T10:56:40.333Z", :build_time_millis 743735, :queued_time_millis 8017, :outcome "failed"} {:build_num 136873, :start_time "2016-02-11T11:16:16.022Z", :build_time_millis 1372018, :queued_time_millis 0, :outcome "success"} {:build_num 136878, :start_time "2016-02-11T12:05:34.787Z", :build_time_millis 1438399, :queued_time_millis 4568, :outcome "success"} {:build_num 136882, :start_time "2016-02-11T13:16:45.071Z", :build_time_millis 1514180, :queued_time_millis 0, :outcome "success"} {:build_num 136883, :start_time "2016-02-11T13:23:13.646Z", :build_time_millis 2253598, :queued_time_millis 1417, :outcome "success"}])

  (defcard build-status-bar-chart
    (om/build build-status-bar-chart {:plot-info default-plot-info
                                      :builds some-builds})))
