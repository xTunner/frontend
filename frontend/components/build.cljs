(ns frontend.components.build
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [frontend.datetime :as datetime]
            [frontend.models.container :as container-model]
            [frontend.models.build :as build-model]
            [frontend.models.plan :as plan-model]
            [frontend.models.project :as project-model]
            [frontend.components.build-config :as build-config]
            [frontend.components.build-head :as build-head]
            [frontend.components.build-invites :as build-invites]
            [frontend.components.build-steps :as build-steps]
            [frontend.components.common :as common]
            [frontend.components.project.common :as project-common]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn report-error [build controls-ch]
  (let [build-id (build-model/id build)
        build-url (:build_url build)]
    ;; XXX add circle.yml errors
    (when (:failed build)
      [:div.alert.alert-danger
       (if-not (:infrastructure_fail build)
         [:div.alert-wrap
          "Error! "
          [:a {:href "/docs/troubleshooting"}
           "Check out common problems "]
          "or "
          [:a {:title "Report an error in how Circle ran this build"
               :on-click #(put! controls-ch [:report-build-clicked {:build-url build-url}])}
           "report this issue"]
          " and we'll investigate."]

         [:div
          "Looks like we had a bug in our infrastructure, or that of our providers (generally "
          [:a {:href "https://status.github.com/"} "GitHub"]
          " or "
          [:a {:href "https://status.aws.amazon.com/"} "AWS"]
          ") We should have automatically retried this build. We've been alerted of"
          " the issue and are almost certainly looking into it, please "
          (common/contact-us-inner controls-ch)
          " if you're interested in the cause of the problem."])])))

(defn container-pill [{:keys [container current-container-id]} owner opts]
  (reify
    om/IRender
    (render [_]
      (html
       (let [container-id (container-model/id container)
             controls-ch (get-in opts [:comms :controls])]
         [:li {:class (when (= container-id current-container-id) "active")}
          [:a.container-selector
           {:on-click #(put! controls-ch [:container-selected container-id])
            :class (container-model/status-classes container)}
           (str "C" (:index container))]])))))

(defn container-pills [container-data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [containers current-container-id]} container-data
            controls-ch (get-in opts [:comms :controls])]
        (html
         [:div.containers.pagination.pagination-centered (when-not (< 1 (count containers))
                                                           {:style {:display "none"}})
          [:ul.container-list
           (for [container containers]
             (om/build container-pill
                       {:container container
                        :current-container-id current-container-id}
                       {:opts opts
                        :react-key (:index container)}))]])))))

(defn show-trial-notice? [plan]
  (and (plan-model/trial? plan)
       (plan-model/trial-over? plan)
       (> 4 (plan-model/days-left-in-trial plan))))

(defn notices [data owner opts]
  (reify
    om/IRender
    (render [_]
      (html
       (let [build-data (:build-data data)
             project-data (:project-data data)
             plan (:plan project-data)
             project (:project project-data)
             build (:build build-data)
             controls-ch (get-in opts [:comms :controls])]
         [:div.row-fluid
          [:div.offset1.span10
           [:div (common/messages (:messages build))]
           [:div (report-error build controls-ch)]

           (when (and plan (show-trial-notice? plan))
             (om/build project-common/trial-notice plan {:opts opts}))

           (when (and project (project-common/show-enable-notice project))
             (om/build project-common/enable-notice project {:opts opts}))

           (when (and project (project-common/show-follow-notice project))
             (om/build project-common/follow-notice project {:opts opts}))

           (when (build-model/display-build-invite build)
             (om/build build-invites/build-invites
                       (:invite-data build-data)
                       {:opts (assoc opts :project-name (vcs-url/project-name (:vcs_url build)))}))

           (when (and (build-model/config-errors? build)
                      (not (:dismiss-config-errors build-data)))
             (om/build build-config/config-errors build {:opts opts}))]])))))

(defn build [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [build (get-in data state/build-path)
            build-data (get-in data state/build-data-path)
            container-data (get-in data state/container-data-path)
            project-data (get-in data state/project-data-path)
            controls-ch (get-in opts [:comms :controls])]
        (html
         [:div#build-log-container
          (if-not build
           [:div
             (common/flashes)
             [:div.loading-spinner common/spinner]]

            [:div
             (om/build build-head/build-head (dissoc build-data :container-data) {:opts opts})
             (common/flashes)
             (om/build notices
                       {:build-data (dissoc build-data :container-data)
                        :project-data project-data}
                       {:opts opts})
             (om/build container-pills container-data {:opts opts})
             (om/build build-steps/container-build-steps container-data {:opts opts})

             (when (< 1 (count (:steps build)))
               [:div (common/messages (:messages build))])])])))))
