(ns frontend.components.builds-table
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [frontend.datetime :as datetime]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.models.build :as build-model]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn build-row [build controls-ch show-actions?]
  (let [url (build-model/path-for (select-keys build [:vcs_url]) build)]
    [:tr {:class (when (:dont_build build) "dont_build")}
     [:td
      [:a
       {:title (str (:username build) "/" (:reponame build) " #" (:build_num build)),
        ;; XXX todo: add include_project logic
        :href url}
       (str (:username build) "/" (:reponame build) " #" (:build_num build))]]
     [:td
      (if-not (:vcs_revision build)
        [:a {:href url}]
        [:a {:title (build-model/github-revision build)
             :href url}
         (build-model/github-revision build)])]
     [:td
      [:a
       {:title (build-model/branch-in-words build)
        :href url}
       (-> build build-model/branch-in-words (utils/trim-middle 23))]]
     [:td
      [:a
       {:title (build-model/author build)
        :href url}
       (build-model/author build)]]
     [:td.recent-log
      [:a
       {:title (:body build)
        :href url}
       (:subject build)]]
     (if (= "not_run" (:status build))
       [:td {:col-span 2}]
       (list [:td.recent-time-started
              [:a
               {:title (datetime/full-datetime (js/Date.parse (:start_time build)))
                :href url}
               (om/build common/updating-duration (:start_time build) {:opts {:formatter datetime/time-ago}}) " ago"]]
             [:td.recent-time-duration
              [:a
               {:title (build-model/duration build)
                :href url}
               (if (build-model/running? build)
                 (om/build common/updating-duration (:start_time build))
                 (build-model/duration build))]]))
     [:td.recent-status-badge
      [:a
       {:title (build-model/status-words build)
        :href url}
       [:span.label.build_status
        {:class (build-model/status-class build)}
        (build-model/status-words build)]]]
     (when show-actions?
       [:td.build_actions
        (when (build-model/can-cancel? build)
          (let [build-id (build-model/id build)]
            ;; XXX: how are we going to get back to the correct build in the app-state?
            (forms/stateful-button
             [:a {:on-click #(put! controls-ch [:cancel-build-clicked build-id])}
              "Cancel"])))])]))

(defn builds-table [builds owner opts]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch (om/get-shared owner [:comms :controls])
            show-actions? (:show-actions? opts)]
        (html
         [:table.recent-builds-table
          [:thead
           [:tr
            [:th "Build"]
            [:th "Revision"]
            ;; XXX show_branch logic
            [:th "Branch"]
            [:th "Author"]
            [:th "Log"]
            ;; XXX show_queued logic
            [:th "Started at"]
            [:th "Length"]
            [:th.condense "Status"]
            (when show-actions?
              [:th.condense "Actions"])]]
          [:tbody
           (map #(build-row % controls-ch show-actions?) builds)]])))))
