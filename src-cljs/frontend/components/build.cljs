(ns frontend.components.build
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.datetime :as datetime]
            [frontend.models.container :as container-model]
            [frontend.models.build :as build-model]
            [frontend.models.plan :as plan-model]
            [frontend.models.project :as project-model]
            [frontend.components.build-config :as build-config]
            [frontend.components.build-head :as build-head]
            [frontend.components.invites :as invites]
            [frontend.components.build-steps :as build-steps]
            [frontend.components.common :as common]
            [frontend.components.project.common :as project-common]
            [frontend.config :refer [enterprise?]]
            [frontend.scroll :as scroll]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]])
    (:require-macros [frontend.utils :refer [html]]))

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
     "Looks like we had a bug in our infrastructure. "
     "We should have automatically retried this build.  Please "
     (common/contact-us-inner owner)
     " so we can investigate the problem if it persists."]))


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
      (scroll/dispose owner))

    ))

(defn container-pills [data owner]
  (reify
    om/IRender
    (render [_]
      (let [container-data (:container-data data)
            build-running? (:build-running? data)
            build (:build data)
            {:keys [containers current-container-id]} container-data
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
                  (when (om/get-shared owner [:ab-tests :parallelism_button_design])
                    [:a.container-selector.parallelism-tab
                     {:role "button"
                      :href (build-model/path-for-parallelism build)
                      :title "adjust parallelism"}
                     [:span "+"]])
                  (when-not (om/get-shared owner [:ab-tests :parallelism_button_design])
                       [:a.container-selector.parallelism-tab-b
                     {:role "button"
                      :href (build-model/path-for-parallelism build)
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
         [:div
          (common/messages (set (:messages build)))
          [:div.container-fluid
           [:div.row
            [:div.col-xs-10.col-xs-offset-1
             (when (empty? (:messages build))
               [:div (report-error build owner)])

             (when (and plan (project-common/show-trial-notice? project plan))
               (om/build project-common/trial-notice project-data))

             (when (plan-model/suspended? plan)
               (om/build project-common/suspended-notice plan))

             (when (and project (project-common/show-enable-notice project))
               (om/build project-common/enable-notice project))

             (if (om/get-shared owner [:ab-tests :follow_notice])
               (when (and project (project-common/show-follow-notice project))
                 (om/build project-common/follow-notice project)))

             (when (build-model/display-build-invite build)
               (om/build invites/build-invites
                         (:invite-data data)
                         {:opts {:project-name (vcs-url/project-name (:vcs_url build))}}))

             (when (and (build-model/config-errors? build)
                        (not (:dismiss-config-errors build-data)))
               (om/build build-config/config-errors build))]]]])))))

(defn build [data owner]
  (reify
    om/IRender
    (render [_]
      (let [build (get-in data state/build-path)
            build-data (get-in data state/build-data-path)
            container-data (get-in data state/container-data-path)
            invite-data (:invite-data data)
            project-data (get-in data state/project-data-path)
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
                                              :user user
                                              :scopes (get-in data state/project-scopes-path)})
             (om/build common/flashes (get-in data state/error-message-path))
             (om/build notices {:build-data (dissoc build-data :container-data)
                                :project-data project-data
                                :invite-data invite-data})
             (om/build container-pills {:container-data container-data
                                        :build-running? (build-model/running? build)
                                        :build build})
             (om/build build-steps/container-build-steps container-data)

             (when (< 1 (count (:steps build)))
               [:div (common/messages (:messages build))])])])))))
