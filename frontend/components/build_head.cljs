(ns frontend.components.build-head
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.components.builds-table :as builds-table]
            [frontend.components.common :as common]
            [frontend.utils :as utils :include-macros true]
            [goog.string :as gstring]
            goog.string.format
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn build-queue [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [build builds controls-ch]} data]
        (html
         (if-not builds
           [:div.loading-spinner common/spinner]
           [:div
            (when-not (build-model/in-usage-queue? build)
              [:p (str "Circle " (when-not (build-model/in-run-queue? build) "has")
                       " spent " (build-model/run-queued-time build)
                       " acquiring containers for this build")])

            (when (seq builds)
              ;; XXX this could still use some work
              [:p (str "This build " (if (build-model/in-usage-queue? build)
                                       "has been"
                                       "was")
                       " queued behind the following builds for "
                       (build-model/usage-queued-time build))])

            (om/build builds-table/builds-table {:builds builds
                                                 :controls-ch controls-ch
                                                 :show-actions? true})]))))))

(defn commit-line [{:keys [subject body commit_url commit] :as commit-details}]
  [:div
   ;; XXX add tooltips
   [:span {:title body}
    subject " "]
   [:a.sha-one {:href commit_url
                :title commit}
    (subs commit 0 7)
    [:i.fa.fa-github]]])

(defn build-commits [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [build (:build data)
            controls-ch (:controls-ch data)
            build-id (build-model/id build)]
        (html
         [:section.build-commits {:class (when (:show-all-commits build) "active")}
          [:div.build-commits-title
           [:strong "Commit Log"]
           (when (:compare build)
             [:a.compare {:href (:compare build)}
              "compare"
              [:i.fa.fa-github]])
           (when (< 3 (count (:all_commit_details build)))
             [:a {:role "button"
                  :on-click #(put! controls-ch [:toggle-show-all-commits build-id])}
              (str (- (count (:all_commit_details build)) 3) " more ")
              [:i.fa.fa-caret-down]])]
          [:div.build-commits-list
           (if-not (seq (:all_commit_details build))
             (commit-line {:subject (:subject build)
                           :body (:body build)
                           :commit_url (build-model/github-commit-url build)
                           :commit (:vcs_revision build)})
             (list
              (map commit-line (take 3 (:all_commit_details build)))
              (when (:show-all-commits build)
                (map commit-line (drop 3 (:all_commit_details build))))))]])))))

(defn build-ssh [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [build (:build data)
            nodes (:node build)]
        (html
         [:section.build-ssh
          [:div.build-ssh-title
           [:strong "SSH Info "]
           [:i.fa.fa-question-circle
            ;; XXX popovers
            {:title "You can SSH into this build. Use the same SSH public key that you use for GitHub. SSH boxes will stay up for 30 minutes. This build takes up one of your concurrent builds, so cancel it when you are done."}]]
          [:div.build-ssh-list
           [:dl.dl-horizontal
            (map (fn [node]
                   (list
                    [:dt (when (> 1 (count nodes)) [:span (:index node)])]
                    [:dd {:class (when (:ssh_enabled node) "connected")}
                     [:span (gstring/format "ssh -p %s %s@%s " (:port node) (:username node) (:ip_addr node))]
                     (when-not (:ssh_enabled node)
                       [:span.loading-spinner common/spinner])]))
                 nodes)]]])))))

(defn build-artifacts-list [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [build (:build data)
            controls-ch (:controls-ch data)
            build-id (build-model/id build)
            artifacts (:artifacts build)]
        (html
         [:section.build-artifacts {:class (when (:show-artifacts build) "active")}
          [:div.build-artifacts-title
           [:strong "Build Artifacts"]
           [:a {:role "button"
                :on-click #(put! controls-ch [:show-artifacts-toggled
                                              {:build-id build-id
                                               :username (:username @build)
                                               :reponame (:reponame @build)
                                               :build_num (:build_num @build)}])}
            [:span " view "]
            [:i.fa.fa-caret-down {:class (when (:show-artifacts build) "fa-rotate-180")}]]]
          (when (:show-artifacts build)
            (if-not artifacts
              [:div.loading-spinner common/spinner]

              [:ol.build-artifacts-list
               (map (fn [artifact]
                      [:li
                       [:a {:href (:url artifact) :target "_blank"}
                        (:pretty_path artifact)]])
                    artifacts)]))])))))

(defn build-head [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [build (:build data)
            controls-ch (:controls-ch data)
            settings (:settings data)
            build-id (build-model/id build)]
        (html
         [:div.build-head-wrapper
          [:div.build-head
           [:div.build-info
            [:table
             [:tbody
              [:tr
               [:th "Author"]
               [:td (if-not (:author_email build)
                      [:span (build-model/author build)]
                      [:a {:href (str "mailto:" (:author_email build))}
                       (build-model/author build)])]
               [:th "Started"]
               [:td (build-model/pretty-start-time build)]]
              [:tr
               [:th "Trigger"]
               [:td (build-model/why-in-words build)]
               [:th "Duration"]
               [:td (build-model/duration build)]]
              [:tr
               [:th "Previous"]
               (if-not (:previous_build build)
                 [:td "none"]
                 [:td
                  [:a {:href (build-model/path-for (select-keys build [:vcs_url])
                                                   (assoc build :build_num (:previous_build build)))}
                   (:previous_build build)]])
               [:th "Status"]
               [:td
                [:span.build-status {:class (:status build)}
                 (build-model/status-words build)]]]
              [:tr
               (when (:usage_queued_at build)
                 (list [:th "Queued"]
                       [:td (build-model/queued-time-summary build)
                        [:a {:on-click #(put! controls-ch [:usage-queue-why-toggled
                                                           {:build-id build-id
                                                            :username (:username @build)
                                                            :reponame (:reponame @build)
                                                            :build_num (:build_num @build)}])}
                         " view "]
                        [:i.fa.fa-caret-down {:class (when (:show-usage-queue build) "fa-rotate-180")}]]))
               (when (build-model/author-isnt-committer build)
                 [:th "Committer"]
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
                 (str (:parallel build) "x")]]]]]
            [:div.build-actions
             [:button.retry_build
              {:data-loading-text "Rebuilding",
               :title "Retry the same tests",
               :on-click #(put! controls-ch [:retry-build-clicked {:build-id build-id
                                                                   :clear-cache? false}])}
              "Rebuild"]
             [:button.clear_cache_retry
              {:data-loading-text "Rebuilding",
               :title "Clear cache and retry",
               :on-click #(put! controls-ch [:retry-build-clicked {:build-id build-id
                                                                   :clear-cache? true}])}
              "w/ cleared cache"]
             [:button.ssh_build
              {:data-loading-text "Rebuilding",
               :title "Retry with SSH in VM",
               :on-click #(put! controls-ch [:ssh-build-clicked build-id])}
              "w/ ssh enabled"]
             [:button.report_build
              {:title "Report error with build",
               :on-click #(put! controls-ch [:report-build-clicked build-id])}
              "Report"]
             (when (build-model/can-cancel? build)
               [:button.cancel_build
                {:data-loading-text "Canceling",
                 :title "Cancel this build",
                 :on-click #(put! controls-ch [:cancel-build-clicked build-id])}
                "Cancel"])]]
           (when (:show-usage-queue build)
             (om/build build-queue
                       {:builds (:usage-queue-builds build)
                        :build build
                        :controls-ch controls-ch}
                       {:opts opts}))
           (when (:subject build)
             (om/build build-commits
                       {:build build
                        :controls-ch controls-ch}
                       {:opts opts}))
           (when (build-model/ssh-enabled-now? build)
             (om/build build-ssh {:build build} {:opts opts}))
           (when (:has_artifacts build)
             (om/build build-artifacts-list
                       {:build build
                        :controls-ch controls-ch}
                       {:opts opts}))]])))))
