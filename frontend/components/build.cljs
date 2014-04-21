(ns frontend.components.build
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.datetime :as datetime]
            [frontend.models.container :as container-model]
            [frontend.models.build :as build-model]
            [frontend.components.build-head :as build-head]
            [frontend.components.build-steps :as build-steps]
            [frontend.components.common :as common]
            [frontend.utils :as utils]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn report-error [build controls-ch]
  (let [build-id (build-model/id build)]
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

(defn build-invite [build] "")
(defn container-build-steps [build] "")

(defn project-trial-notice [] "") ;; should go elsewhere
(defn project-enable-notice [] "") ;; should go elsewhere
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

(defn container-pill [container build controls-ch]
  (let [container-id (container-model/id container)
        build-id (build-model/id build)]
    [:li {:class (when (= container-id (get build :current-container-id 0)) "active")}
     [:a.container-selector
      {:on-click #(put! controls-ch [:container-selected container-id])
       :class (container-model/status-style container)}
      (str "C" (:index container))]]))

(defn build [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [build (:current-build data)
            controls-ch (get-in data [:comms :controls])
            containers (build-model/containers build)]
        (html
         [:div#build-log-container
          (if-not build
            [:div
             (common/flashes)
             [:div.loading-spinner common/spinner]]

            [:div
             (om/build build-head/build-head {:build build
                                              :controls-ch controls-ch
                                              :settings (:settings data)} {:opts opts})
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

             (when (< 1 (count containers))
               [:div.containers.pagination.pagination-centered
                {:data-bind "sticky_waypoint: {offset: 0}"}
                [:ul.container-list
                 (map #(container-pill % build controls-ch)
                      containers)]])
             (om/build build-steps/container-build-steps
                       {:build build
                        :containers containers
                        :controls-ch controls-ch}
                       {:opts opts})

             (when (< 1 (count (:steps build)))
               [:div (common/messages (:messages build))])

             (comment
               [:div.autoscroll-trigger
                {:enable_autoscroll "enable_autoscroll",
                 :waypoint_callback:_ "waypoint_callback:_",
                 :_ "_",
                 :bottom-in-view "bottom-in-view",
                 :offset:_ "offset:_",
                 :data-bind "\\waypoint:"}])])])))))
