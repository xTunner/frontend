(ns frontend.components.build
  (:require [frontend.async :refer [raise!]]
            [frontend.analytics :as analytics]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.models.container :as container-model]
            [frontend.models.feature :as feature]
            [frontend.models.plan :as plan-model]
            [frontend.models.project :as project-model]
            [frontend.components.build-config :as build-config]
            [frontend.components.build-head :as build-head]
            [frontend.components.invites :as invites]
            [frontend.components.build-steps :as build-steps]
            [frontend.components.common :as common]
            [frontend.components.project.common :as project-common]
            [frontend.components.svg :refer [svg]]
            [frontend.config :as config]
            [frontend.config :refer [enterprise?]]
            [frontend.scroll :as scroll]
            [frontend.state :as state]
            [frontend.timer :as timer]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true])
    (:require-macros [frontend.utils :refer [html]]))

(def view "build")

(defn infrastructure-fail-message [owner]
  (if-not (enterprise?)
    [:div
     "Looks like we had a bug in our infrastructure, or that of our providers (generally "
     [:a {:href "https://status.github.com/"} "GitHub"]
     " or "
     [:a {:href "https://status.aws.amazon.com/"} "AWS"]
     ") We should have automatically retried this build. We've been alerted of"
     " the issue and are almost certainly looking into it, please "
     (common/contact-us-inner owner)
     " if you're interested in the cause of the problem."]

    [:div
     "Looks like you may have encountered a bug in the build infrastructure. "
     "Your build should have been automatically retried.  If the problem persists, please "
     (common/contact-us-inner owner)
     ", so CircleCI can investigate."]))


(defn report-error [build owner]
  (let [build-id (build-model/id build)
        build-url (:build_url build)]
    (when (:failed build)
      [:div.alert.alert-danger
       (if (:infrastructure_fail build)
         (infrastructure-fail-message owner)
         [:div.alert-wrap
          "Error! "
          [:a {:href "/docs/troubleshooting"}
           "Check out common problems "]
          "or, if there's a problem in how CircleCI ran this build, "
          [:a (merge {:title "Report an error in how Circle ran this build"}
                     (common/contact-support-a-info owner :tags [:report-build-clicked {:build-url build-url}]))
           "report this issue"]
          " and we'll investigate."])])))

(defn container-pill [{:keys [container current-container-id build-running?]} owner]
  (reify
    om/IRender
    (render [_]
      (html
       (let [container-id (container-model/id container)
             status (container-model/status container build-running?)]
        [:a.container-selector
         {:on-click #(raise! owner [:container-selected {:container-id container-id}])
          :role "button"
          :class (concat (container-model/status->classes status)
                         (when (= container-id current-container-id) ["active"]))}
         (str (:index container))
         (case status
           :failed (common/ico :fail-light)
           :success (common/ico :pass-light)
           :canceled (common/ico :fail-light)
           :running (common/ico :logo-light)
           :waiting (common/ico :none-light)
           nil)])))))

(defn sticky [{:keys [wrapper-class content-class content]} owner]
  (reify

    om/IRenderState
    (render-state [_ {:keys [stick]}]
      (let [wrapper-style (when stick
                            {:height (:height stick)})
            content-style (when stick
                            {:position :fixed
                             :top 0
                             :left (:left stick)
                             :width (:width stick)})]
        (html [:div {:ref "wrapper" :class wrapper-class :style wrapper-style}
               [:div {:ref "content" :class content-class :style content-style}
                content]])))

    om/IDidMount
    (did-mount [_]
      (scroll/register owner
        #(let [wrapper (om/get-node owner "wrapper")
               content (om/get-node owner "content")
               wrapper-rect (.getBoundingClientRect wrapper)
               content-height (.-height (.getBoundingClientRect content))
               stick? (<= (.-top wrapper-rect) 0)]
           (om/set-state! owner :stick
             (when stick?
               {:left (.-left wrapper-rect)
                :width (.-width wrapper-rect)
                :height content-height})))))

    om/IWillUnmount
    (will-unmount [_]
      (scroll/dispose owner))))

(defn container-pills [{:keys [build-running? build container-data project-data user view]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (analytics/track-parallelism-button-impression  {:view view
                                                       :project-data project-data
                                                       :user user}))
    om/IRender
    (render [_]
      (let [{:keys [containers current-container-id]} container-data
            hide-pills? (or (>= 1 (count containers))
                            (empty? (remove :filler-action (mapcat :actions containers))))
            style {:position "fixed"}
            div (html
                  [:div.container-list
                   (for [container containers]
                     (om/build container-pill
                               {:container container
                                :build-running? build-running?
                                :current-container-id current-container-id}
                               {:react-key (:index container)}))
                   (if (and
                         user
                         (contains? (:plan project-data) :paid)
                         (not (get-in project-data [:plan :paid]))
                         (not (get-in project-data [:project :feature_flags :oss]))
                         (= :button (om/get-shared owner  [:ab-tests :upgrade_banner])))
                     [:a.container-selector.parallelism-tab.upgrade
                      {:role "button"
                       :href (build-model/path-for-parallelism build)
                       :on-click #(analytics/track-parallelism-button-click {:view view
                                                                             :project-data project-data
                                                                             :user user})
                       :title "adjust parallelism"}
                      [:span "Add Containers +"]]
                     [:a.container-selector.parallelism-tab
                      {:role "button"
                       :href (build-model/path-for-parallelism build)
                       :on-click #(analytics/track-parallelism-button-click {:view view
                                                                             :project-data project-data
                                                                             :user user})
                       :title "adjust parallelism"}
                      [:span "+"]])])]
        (om/build sticky {:content div :content-class "containers"})))))

(defn notices [data owner]
  (reify
    om/IRender
    (render [_]
      (html
       (let [build-data (:build-data data)
             project-data (:project-data data)
             plan (:plan project-data)
             project (:project project-data)
             build (:build build-data)]
         [:div.notices
          (common/messages (set (:messages build)))
          [:div.row
           [:div.col-xs-12
            (when (empty? (:messages build))
              [:div (report-error build owner)])

            (when (and plan (project-common/show-trial-notice? project plan))
              (om/build project-common/trial-notice project-data))

            (when (plan-model/suspended? plan)
              (om/build project-common/suspended-notice plan))

            (when (and project (project-common/show-enable-notice project))
              (om/build project-common/enable-notice project))

            (when (build-model/display-build-invite build)
              (om/build invites/build-invites
                        (:invite-data data)
                        {:opts {:project-name (vcs-url/project-name (:vcs_url build))}}))

            (when (and (build-model/config-errors? build)
                       (not (:dismiss-config-errors build-data)))
              (om/build build-config/config-errors build))]]])))))

(defn upgrade-banner [{:keys [build project-data user view]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (analytics/track-parallelism-button-impression  {:view view
                                                       :project-data project-data
                                                       :user user}))
    om/IRender
    (render [_]
      (html
        [:div.upgrade-banner
         [:i.fa.fa-tachometer.fa-lg]
         [:p.main.message [:b "Build Diagnostics"]
          [:p.sub.message "Looking for faster builds? "
           [:a {:href (build-model/path-for-parallelism build)
                :on-click #(analytics/track-parallelism-button-click {:view view
                                                                      :project-data project-data
                                                                      :user user})}
            "Adding containers"]
           " can cut down time spent testing."]]]))))

(defn get-decorated-project [project-data]
  (let [project (:project project-data)
        plan (:plan project-data) 
        show-build-timing? (or (config/enterprise?)
                               (:oss project)
                               (> (:containers plan) 1))]
    (-> project
        (assoc :show-build-timing? show-build-timing?)
        (assoc :org-name (:org_name plan)))))

(defn build-v1 [data owner]
  (reify
    om/IRender
    (render [_]
      (let [build (get-in data state/build-path)
            build-data (get-in data state/build-data-path)
            container-data (get-in data state/container-data-path)
            invite-data (:invite-data data)
            project-data (get-in data state/project-data-path)
            projects (get-in data state/projects-path)
            user (get-in data state/user-path)]
        (html
         [:div#build-log-container
          (if-not build
           [:div
             (om/build common/flashes (get-in data state/error-message-path))
             [:div.loading-spinner-big common/spinner]]

            [:div
             (om/build build-head/build-head {:build-data (dissoc build-data :container-data)
                                              :project-data project-data
                                              :project (get-decorated-project project-data)
                                              :user user
                                              :scopes (get-in data state/project-scopes-path)})
             (when (and
                     user
                     (contains? :paid (:plan project-data))
                     (not (get-in project-data [:plan :paid]))
                     (not (get-in project-data [:project :feature_flags :oss]))
                     (= :banner (om/get-shared owner  [:ab-tests :upgrade_banner])))
               (om/build upgrade-banner {:build build
                                         :project-data project-data
                                         :user user
                                         :view view}))
             (om/build notices {:build-data (dissoc build-data :container-data)
                                :project-data project-data
                                :invite-data invite-data})
             (om/build container-pills {:build build
                                        :build-running? (build-model/running? build)
                                        :container-data container-data
                                        :project-data project-data
                                        :user user
                                        :view view})
             (om/build build-steps/container-build-steps container-data)

             (when (< 1 (count (:steps build)))
               [:div (common/messages (:messages build))])])])))))

(defn container-result-icon [{:keys [name]} owner]
  (reify
    om/IRender
    (render [_]
      (om/build svg {:class "container-status-icon"
                              :src (utils/cdn-path (str "/img/inner/icons/" name ".svg"))}))))

(defn last-action-end-time
  [container]
  (-> (filter #(not (:filler-action %)) (:actions container)) last :end_time))

(defn action-duration-ms
  [{:keys [start_time end_time] :as action}]
  (if (and start_time end_time)
    (let [start (.getTime (js/Date. start_time))
          end (if end_time
                (.getTime (js/Date. end_time))
                (datetime/server-now))]
      (- end start))
      0))

;; TODO this seems a little bit slow when calculating durations for really complex builds with
;; lots of containers. Is there a good way to avoid recalculating this when selecting container pills? (Perhaps caching the calculated value using IWillUpdate / IShouldUpdate?)
(defn container-utilization-duration
  [actions]
  (apply + (map action-duration-ms actions)))


(defn container-duration-label [{:keys [actions]}]
  (reify
    om/IRender
    (render [_]
      (html
        [:span.lower-pill-section
         "("
         (datetime/as-duration (container-utilization-duration actions))
         ")"]))))

(defn container-pill-v2 [{:keys [container current-container-id build-running?]} owner]
  (reify
    om/IDisplayName
    (display-name [_] "Container Pill v2")
    om/IDidMount
    (did-mount [_]
      (timer/set-updating! owner (not (last-action-end-time container))))
    om/IDidUpdate
    (did-update [_ _ _]
      (timer/set-updating! owner (not (last-action-end-time container))))
    om/IRender
    (render [_]
      (html
       (let [container-id (container-model/id container)
             status (container-model/status container build-running?)
             duration-ms (container-utilization-duration container)]
         [:a.container-selector-v2
          {:on-click #(raise! owner [:container-selected {:container-id container-id}])
           :class (concat (container-model/status->classes status)
                          (when (= container-id current-container-id) ["active"]))}
          [:span.upper-pill-section
           [:span.container-index (str (:index container))]
           [:span.status-icon
            (om/build container-result-icon {:name (case status
                                                     :failed "Status-Failed"
                                                     :success "Status-Passed"
                                                     :canceled "Status-Canceled"
                                                     :running "Status-Running"
                                                     :waiting "Status-Queued"
                                                     nil)})]]
          (om/build container-duration-label {:actions (:actions container)})])))))

(def paging-width 10)

(defn container-pills-v2 [data owner]
  (reify
    om/IDisplayName
    (display-name [_]
      "Container Pills")
    om/IInitState
    (init-state [_]
      {:paging-offset 0})
    om/IRenderState
    (render-state [_ state]
      (let [container-data (:container-data data)
            build-running? (:build-running? data)
            build (:build data)
            {:keys [containers current-container-id]} container-data
            container-count (count containers)
            paging-offset (:paging-offset state)
            previous-container-count (max 0 (- paging-offset 1))
            subsequent-container-count (min paging-width (- container-count (+ paging-offset paging-width)))

            hide-pills? (or (>= 1 (count containers))
                            (empty? (remove :filler-action (mapcat :actions containers))))
            style {:position "fixed"}
            div (html
                 [:div.container-list-v2
                  (if (> previous-container-count 0)
                    [:a.container-selector-v2.page-container-pills
                     {:on-click #(om/set-state! owner :paging-offset (- paging-offset paging-width))}
                     [:div.nav-caret
                      [:i.fa.fa-2x.fa-angle-left]]
                     [:div.pill-details ;; just for flexbox container
                      [:div "Previous " paging-width]
                      [:div (count containers) " total"]]])
                  (for [container (subvec containers
                                          paging-offset
                                          (min container-count (+ paging-offset paging-width)))]
                    (om/build container-pill-v2
                              {:container container
                               :build-running? build-running?
                               :current-container-id current-container-id}
                              {:react-key (:index container)}))
                  (if (> subsequent-container-count 0)
                    [:a.container-selector-v2.page-container-pills
                     {:on-click #(om/set-state! owner :paging-offset (+ paging-offset paging-width))}
                     [:div.pill-details ;; just for flexbox container
                      [:div "Next " subsequent-container-count]
                      [:div container-count " total"]]
                     [:div.nav-caret
                      [:i.fa.fa-2x.fa-angle-right]]]
                    [:a.container-selector-v2.add-containers
                     {:href (build-model/path-for-parallelism build)
                      :title "Adjust parallelism"}
                     "+"])])]
        (om/build sticky {:content div :content-class "containers-v2"})))))

(def css-trans-group (-> js/React (aget "addons") (aget "CSSTransitionGroup")))

(defn transition-group
  [opts component]
  (let [[group-name enter? leave? appear? class-name]
        (if (map? opts)
          [(:name opts)
           (:enter opts)
           (:leave opts)
           (:appear opts)
           (:class opts)]
          [opts true true false nil])]
    (apply
      css-trans-group
      #js {:transitionName group-name
           :transitionEnter enter?
           :transitionLeave leave?
           :transitionAppear appear?
           :component "div"
           :className class-name}
      component)))

(defn selected-container-index [data]
  (get-in data [:current-build-data :container-data :current-container-id]))

(defn build-v2 [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:action-transition-direction "steps-ltr"})
    om/IWillReceiveProps
    (will-receive-props [_ next-props]
      (let [old-ix (selected-container-index (om/get-props owner))
            new-ix (selected-container-index next-props)]
        (om/set-state! owner
                       :action-transition-direction
                       (if (> old-ix new-ix)
                         "steps-ltr"
                         "steps-rtl"))))
    om/IRender
    (render [_]
      (let [build (get-in data state/build-path)
            build-data (get-in data state/build-data-path)
            container-data (get-in data state/container-data-path)
            invite-data (:invite-data data)
            project-data (get-in data state/project-data-path)
            user (get-in data state/user-path)]
        (html
         [:div.build-info-v2
          (if-not build
           [:div
             (om/build common/flashes (get-in data state/error-message-path))
             [:div.loading-spinner-big common/spinner]]

            [:div
             (om/build build-head/build-head-v2 {:build-data (dissoc build-data :container-data)
                                                 :project-data project-data
                                                 :project (get-decorated-project project-data)
                                                 :user user
                                                 :scopes (get-in data state/project-scopes-path)})
             [:div.card.col-sm-12

              (om/build common/flashes (get-in data state/error-message-path))

              (om/build notices {:build-data (dissoc build-data :container-data)
                                 :project-data project-data
                                 :invite-data invite-data})

              (om/build container-pills-v2 {:container-data container-data
                                            :build-running? (build-model/running? build)
                                            :build build})

              (transition-group {:name (om/get-state owner :action-transition-direction)
                                 :enter true
                                 :leave true
                                 :class "build-steps-animator"}
                                [(om/build build-steps/container-build-steps-v2
                                           container-data
                                           {:key :current-container-id})])]])])))))

(defn build []
  (if (feature/enabled? :ui-v2)
    build-v2
    build-v1))
