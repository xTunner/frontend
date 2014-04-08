(ns frontend.components.build
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.components.build-top-table :as build-top-table]
            [frontend.components.common :as common]
            [frontend.utils :as utils]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

;; get from converted-templates
(defn messages [build]
  [:div.row-fluid
   (when (pos? (count (:messages build)))
     [:div#build-messages.offset1.span10
      {:data-bind "foreach: messages"}
      (map (fn [message]
             [:div.alert.alert-info
              [:strong "Warning: "]
              [:span {:dangerouslySetInnerHTML #js {"__html" (:message message)}}]])
           (:messages build))])])

(defn build-invite [build] "")
(defn container-build-steps [build] "")

(defn project-trial-notice [] "") ;; should go elsewhere
(defn project-enable-notice [] "") ;; should go elsewhere

(defn build [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [build (:current-build data)
            controls-ch (get-in data [:comms :controls])]
        (html
         [:div#build-log-container
          [:div
           {:data-bind "ifnot: build_has_been_loaded"}
           (common/flashes)
           [:div.loading-spinner common/spinner]]
          [:div
           {:data-bind "if: build_has_been_loaded"}
           [:div
            [:div
             (om/build build-top-table/build-top-table {:build build
                                                        :ch controls-ch
                                                        :settings (:settings data)} {:opts opts})]
            [:div
             (common/flashes)]
            [:div.row-fluid
             [:div.offset1.span10
              [:div
               {:data-bind "if: messages().length"}
               (messages build)]
              [:div {:data-bind "ifnot: messages().length"}]
              [:div
               {:data-bind
                "if: $root.project() && $root.project().show_build_page_trial_notice"}
               (project-trial-notice)]
              [:div
               {:data-bind
                "if: $root.project() && $root.project().show_enable_project_notice"}
               (project-enable-notice)]
              [:div.row-fluid
               {:data-bind "if: $root.show_follow_project_button"}
               [:div.offset1.span10
                [:div.alert.alert-success
                 [:button.btn.btn-primary
                  {:data-loading-text "Following...",
                   :data-bind "click: VM.project().follow"}
                  "Follow"
                  [:span {:data-bind "text: VM.project().project_name()"}]]
                 "to add"
                 [:span {:data-bind "text: VM.project().project_name()"}]
                 "to your sidebar and get build notifications."]]]
              "<!-- ko if: display_first_green_build_invitations -->"
              (build-invite build)
              "<!-- /ko -->"]]
            "<!-- ko if: feature_enabled(\\build_GH1157_container_oriented_ui\\) && containers().length > 1 -->"
            [:div.containers.pagination.pagination-centered
             {:data-bind "sticky_waypoint: {offset: 0}"}
             [:ul.container-list
              {:data-bind "foreach: containers"}
              [:li
               {:data-bind
                "css: { active: $data === $parent.current_container() }"}
               [:a.container-selector
                {:data-bind
                 "text: name , click: $parent.select_container , css: status_style",
                 :href "#"}]]]]
            "<!-- /ko -->"
            "<!-- ko if: feature_enabled(\\build_GH1157_container_oriented_ui\\) -->"
            (container-build-steps build)
            "<!-- /ko -->"
            "<!-- ko ifnot: feature_enabled(\\build_GH1157_container_oriented_ui\\) -->"
            " + $c(HAML.build_steps({}))"
            "<!-- /ko -->"
            [:div
             {:data-bind "if: steps().length > 1"}
             (do
               (print "WTF")
               (messages build))]
            [:div.autoscroll-trigger
             {:enable_autoscroll "enable_autoscroll",
              :waypoint_callback:_ "waypoint_callback:_",
              :_ "_",
              :bottom-in-view "bottom-in-view",
              :offset:_ "offset:_",
              :data-bind "\\waypoint:"}]]]])))))
