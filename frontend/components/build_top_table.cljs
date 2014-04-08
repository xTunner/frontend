(ns frontend.components.build-top-table
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.components.common :as common]
            [frontend.utils :as utils]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

;; XXX finish usage-queued-table
(defn usage-queue-table [data owner opts]
  (reify
    om/IRender
    (render [_]
      (html [:div "I don't do much yet"]))))

;; XXX finish build-commit-log
(defn build-commit-log [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [build (:build data)]
        (html [:div "I don't do much yet"])))))

(defn build-top-table [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [username reponame build_num] :as build} (:build data)
            ch (:ch data)
            build-id (build-model/id build)
            settings (:settings data)]
        (html
         [:div#build-log-header.build-table.row-fluid
          [:div.build-page-header.offset1.span10
           [:div.build-info-actions
            [:table.build-table-top
             [:tbody
              [:tr
               [:th "Author"]
               [:td
                ;; TODO: why is this committer_email and not author_email?
                (if-not (:committer_email build)
                  [:span (build-model/author build)]
                  [:a {:href (str "mailto:" (:committer_email build))}
                   (build-model/author build)])]
               [:th "Started at"]
               [:td (build-model/pretty-start-time build)]]
              [:tr
               [:th "Started by"]
               [:td (build-model/why-in-words build)]
               [:th "Duration"]
               [:td (build-model/duration build)]]
              [:tr
               (when (:previous_build build)
                 [:th "Previous build"]
                 [:td
                  ;; TODO: use secretary for this!
                  [:a {:href (build-model/path-for (select-keys build [:vcs_url])
                                                   (assoc build :build_num (:previous_build build)))}
                   (:previous_build build)]])
               [:th "Status"]
               [:td
                [:span.label {:class (build-model/status-class build)}
                 (build-model/status-words build)]]]
              [:tr
               (when (:usage_queued_at build)
                 [:th.queued-def {:on-click #(put! ch [:usage-queue-why-toggled build-id])}
                  {:data-bind "click: toggle_usage_queue_why"}
                  "Queued Time"
                  [:i.fa.fa-caret-down (when (get-in settings [:builds build-id :show-usage-queue]) "fa-rotate-180")]]
                 [:td (build-model/queued-time-summary build)])
               (when (build-model/author-isnt-committer build)
                 [:td "Committer"]
                 [:td
                  (if-not (:committer_email build)
                    [:span (build-model/committer build)]
                  [:a {:href (str "mailto:" (:committer_email build))}
                   (build-model/committer build)])])]
              [:tr
               [:th "Parallelism"]
               [:td
                [:a {:title (str "This build used " (:parallel build) " containers. Click here to change parallelism for future builds.")
                     :href (build-model/path-for-parallelism build)}
                 (str (:parallel build) "x")]]]
              [:tr [:td {:colspan "4"}
                    (om/build usage-queue-table {} {:opts opts})]]
              [:tr [:td {:colspan "4"}
                    (om/build build-commit-log {:build build} {:opts opts})]]]]
            [:dl.actions
             [:dd

              [:button.btn.btn-danger
               (let [build-url (:build_url build)]
                 {:title "Report an error in how Circle ran this build",
                  :on-click #(put! ch [:intercom-dialog-raised (str "I think I found a bug in Circle at " build-url "\n\n")])})
               "Report..."]
              [:div.btn-group
               [:button.btn.btn-primary
                {:title "Rerun the same version of the tests",
                 :on-click (fn [& args] (put! ch [:retry-build-clicked {:username username
                                                                        :reponame reponame
                                                                        :build_num build_num
                                                                        :build-id build-id}]))
                 :disabled (= :started (:retry-state build))}
                ;; XXX this is horrible, should probably be extracted into a component
                ;; XXX we need to flash failed somewhere
                (if (= :started (:retry-state build))
                  "Triggering..."
                  "Retry")]
               [:button.btn.btn-primary.dropdown-toggle
                {:data-toggle "dropdown"}
                [:span.caret]]
               [:ul.dropdown-menu
                [:li
                 [:a
                  {:data-bind "click: clear_cache_and_retry_build", :href "#"}
                  "Clear Cache & Retry"]]]]
              [:button.btn.btn-primary
               {:data-success-text "Triggered",
                :data-loading-text "Triggering...",
                :title "Rerun the tests, with SSH into the VM",
                :data-bind "click: ssh_build"}
               "SSH"]
              [:span
               {:data-bind "if: can_cancel()"}
               [:button.btn.btn-primary
                {:data-success-text "Canceled",
                 :data-loading-text "Canceling...",
                 :title "Cancel the build immediately",
                 :data-bind "click: cancel_build"}
                "Cancel"]]]]]
           "<!-- ko if: ssh_enabled_now() -->"
           [:dl.ssh_info
            [:dt "SSH Info"]
            [:dd " + $c(HAML.ssh_info_table({}))"]]
           "<!-- /ko -->"
           "<!-- ko if: has_artifacts -->"
           [:dl.artifacts]
           [:div.artifacts-def
            {:data-bind "click: toggle_artifacts"}
            [:dl
             [:dt
              "Build Artifacts"
              [:span
               [:i.fa.fa-caret-down
                {:_ "_",
                 :artifacts_visible "artifacts_visible",
                 :fa-rotate-180:_ "fa-rotate-180:_",
                 :data-bind "\\css:"}]]]
             [:dd]]]
           " + $c(HAML.artifacts_table({}))"
           "<!-- /ko -->"]])))))
