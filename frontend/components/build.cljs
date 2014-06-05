(ns frontend.components.build
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.datetime :as datetime]
            [frontend.models.container :as container-model]
            [frontend.models.build :as build-model]
            [frontend.components.build-head :as build-head]
            [frontend.components.build-steps :as build-steps]
            [frontend.components.common :as common]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn report-error [build controls-ch]
  (let [build-id (build-model/id build)]
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
               :on-click #(put! controls-ch [:report-build-clicked build-id])}
           "report this issue "]
          "and we'll investigate."]

         [:div
          "Looks like we had a bug in our infrastructure, or that of our providers (generally "
          [:a {:href "https://status.github.com/"} "GitHub"]
          " or "
          [:a {:href "https://status.aws.amazon.com/"} "AWS"]
          ") We should have automatically retried this build. We've been alerted of"
          " the issue and are almost certainly looking into it, please "
          (common/contact-us-inner controls-ch)
          " if you're interested in the cause of the problem."])])))

(defn build-invite [build]
  build-invite-isnt-finished
  "")
(defn config-diagnostics [build]
  config-diagnostics-isnt-finished
  "")

(defn project-trial-notice [] ;; should go elsewhere
  project-trial-notice-isnt-finished
  "")
(defn project-enable-notice [] ;; should go elsewhere
  project-enable-notice-inst-finished
  "")
(defn project-follow-button []
  [:div.offset1.span10
   [:div.alert.alert-success
    [:button.btn.btn-primary
     {:data-loading-text "Following...",
      :data-bind "click: VM.project().follow"}
     "Follow"
     [:span {:data-bind "text: VM.project().project_name()"}]]
    "to add"
    [:span {:data-bind "text: VM.project().project_name()"}]
    "to your sidebar and get build notifications."]]
  ;; XXX Displays nothing for now
  "")

(defn container-pills [container-data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [containers current-container-id]} container-data
            controls-ch (get-in opts [:comms :controls])]
        (html
         [:div.containers.pagination.pagination-centered
          {:data-bind "sticky_waypoint: {offset: 0}"}
          [:ul.container-list
           (for [container containers
                 :let [container-id (container-model/id container)]]
             [:li {:class (when (= container-id current-container-id) "active")}
              [:a.container-selector
               {:on-click #(put! controls-ch [:container-selected container-id])
                :class (container-model/status-classes container)}
               (str "C" (:index container))]])]])))))

(defn build [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [build (get-in data state/build-path)
            build-data (get-in data state/build-data-path)
            container-data (get-in data state/container-data-path)
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
             [:div.row-fluid
              [:div.offset1.span10
               [:div (common/messages (:messages build))]
               [:div (report-error build controls-ch)]
               [:div {:data-bind "if: $root.project() && $root.project().show_build_page_trial_notice"}
                (project-trial-notice)]
               [:div {:data-bind "if: $root.project() && $root.project().show_enable_project_notice"}
                (project-enable-notice)]
               [:div.row-fluid
                {:data-bind "if: $root.show_follow_project_button"}
                (project-follow-button)]
               (when (build-model/display-build-invite build)
                 (build-invite build))]]

             (when (< 1 (count (:containers container-data)))
               (om/build container-pills container-data {:opts opts}))
             (om/build build-steps/container-build-steps container-data {:opts opts})

             (when (< 1 (count (:steps build)))
               [:div (common/messages (:messages build))])])])))))
