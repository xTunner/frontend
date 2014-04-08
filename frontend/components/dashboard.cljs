(ns frontend.components.dashboard
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.components.sidebar :as sidebar]
            [frontend.utils :as utils]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn build-row [nav-ch build]
  (let [url (build-model/path-for (select-keys build [:vcs_url]) build)]
    [:tr {:class (when (:dont_build build) "dont_build")}
     [:td
      [:a
       {:title (str (:username build) "/" (:reponame build) " #" (:build_num build)),
        ;; XXX todo: add include_project logic
        :href url}
       (str (:username build) "/" (:reponame build) " #" (:build_num build))]]
     [:td
      [:a
       {:title (build-model/github-revision build)
        :href url}
       (build-model/github-revision build)]]
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
               (build-model/pretty-start-time build)]]
             [:td.recent-time-duration
              [:a
               {:title (build-model/duration build)
                :href url}
               (build-model/duration build)]]))
     [:td
      [:a.recent-status-badge
       {:title (build-model/status-words build)
        :href url}
       [:span.label.build_status
        {:class (build-model/status-class build)}
        (build-model/status-words build)]]]]))

(defn dashboard [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [builds (:recent-builds data)
            nav-ch (get-in data [:comms :nav])]
        (html
         ;; XXX logic for dashboard not ready
         ;; XXX logic for show add projects
         ;; XXX logic for trial notices
         ;; XXX logic for show_build_table
         [:div#dashboard
          (om/build sidebar/sidebar data {:opts opts})
          [:section
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
              [:th "Status"]]]
            [:tbody
             (map (partial build-row nav-ch) builds)]]]])))))
