(ns frontend.components.build-head
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as string]
            [frontend.async :refer [put!]]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.components.builds-table :as builds-table]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.routes :as routes]
            [frontend.utils :as utils :include-macros true]
            [goog.string :as gstring]
            [goog.string.format]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn build-queue [data owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [build builds]} data
            controls-ch (om/get-shared owner [:comms :controls])
            run-queued? (build-model/in-run-queue? build)
            usage-queued? (build-model/in-usage-queue? build)
            plan (:plan data)]
        (html
         (if-not builds
           [:div.loading-spinner common/spinner]
           [:div.build-queue.active
            (when-not usage-queued?
              [:p "Circle " (when run-queued? "has") " spent "
               (om/build common/updating-duration {:start (:queued_at build)
                                                   :stop (or (:start_time build) (:stop_time build))})
               " acquiring containers for this build."])
            (when (< 10000 (build-model/run-queued-time build))
              [:p#circle_queued_explanation
               "We're sorry; this is our fault. Typically you should only see this when load spikes overwhelm our auto-scaling; waiting to acquire containers should be brief and infrequent."])

            (when (seq builds)
              (list
               [:p "This build " (if usage-queued? "has been" "was")
                " queued behind the following builds for "
                (om/build common/updating-duration {:start (:usage_queued_at build)
                                                    :stop (or (:queued_at build) (:stop_time build))})]

               (om/build builds-table/builds-table builds {:opts {:show-actions? true}})))
            (when (and plan
                       (< 10000 (build-model/usage-queued-time build))
                       (> 10000 (build-model/run-queued-time build)))
              [:p#additional_containers_offer
               "Too much waiting? You can " [:a {:href (routes/v1-org-settings-subpage {:org (:org_name plan)
                                                                                        :subpage "containers"})}
                                             "add more containers"]
               " and finish even faster."])]))))))

(defn commit-line [{:keys [subject body commit_url commit] :as commit-details} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (when (seq body)
        (utils/tooltip (str "#commit-line-tooltip-hack-" commit) {:placement "bottom" :animation false})))
    om/IRender
    (render [_]
      (html
       [:div
        [:span {:title body
                :id (str "commit-line-tooltip-hack-" commit)}
         subject " "]
        [:a.sha-one {:href commit_url
                     :title commit}
         " "
         (subs commit 0 7)
         " "
         [:i.fa.fa-github]]]))))

(defn build-commits [build owner]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch (om/get-shared owner [:comms :controls])
            build-id (build-model/id build)]
        (html
         [:section.build-commits {:class (when (:show-all-commits build) "active")}
          [:div.build-commits-title
           [:strong "Commit Log"]
           (when (:compare build)
             [:a.compare {:href (:compare build)}
              "compare "
              [:i.fa.fa-github]])
           (when (< 3 (count (:all_commit_details build)))
             [:a {:role "button"
                  :on-click #(put! controls-ch [:toggle-show-all-commits build-id])}
              (str (- (count (:all_commit_details build)) 3) " more ")
              [:i.fa.fa-caret-down]])]
          [:div.build-commits-list
           (if-not (seq (:all_commit_details build))
             (om/build commit-line {:subject (:subject build)
                                    :body (:body build)
                                    :commit_url (build-model/github-commit-url build)
                                    :commit (:vcs_revision build)})
             (list
              (om/build-all commit-line (take 3 (:all_commit_details build)))
              (when (:show-all-commits build)
                (om/build-all commit-line (drop 3 (:all_commit_details build))))))]])))))

(defn build-ssh [nodes owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (utils/popover "#ssh-popover-hack"
                     {:placement "right"
                      :content "You can SSH into this build. Use the same SSH public key that you use for GitHub. SSH boxes will stay up for 30 minutes. This build takes up one of your concurrent builds, so cancel it when you are done."
                      :title "SSH"}))
    om/IRender
    (render [_]
      (html
       [:section.build-ssh
        [:div.build-ssh-title
         [:strong "SSH Info "]
         [:i.fa.fa-question-circle {:id "ssh-popover-hack" :title "SSH"}]]
        [:div.build-ssh-list
         [:dl.dl-horizontal
          (map (fn [node]
                 (list
                  [:dt (when (> 1 (count nodes)) [:span (:index node)])]
                  [:dd {:class (when (:ssh_enabled node) "connected")}
                   [:span (gstring/format "ssh -p %s %s@%s " (:port node) (:username node) (:public_ip_addr node))]
                   (when-not (:ssh_enabled node)
                     [:span.loading-spinner common/spinner])]))
               nodes)]]
        [:div.build-ssh-doc
         "Debugging Selenium browser tests? "
         [:a {:href "/docs/browser-debugging#interact-with-the-browser-over-vnc"}
          "Read our doc on interacting with the browser over VNC"]
         "."]]))))

(defn cleanup-artifact-path [path]
  (-> path
      (string/replace "$CIRCLE_ARTIFACTS/" "")
      (gstring/truncateMiddle 80)))

(defn build-artifacts-list [data owner]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch (om/get-shared owner [:comms :controls])
            artifacts-data (:artifacts-data data)
            artifacts (:artifacts artifacts-data)
            show-artifacts (:show-artifacts artifacts-data)
            admin? (:admin (:user data))]
        (html
         [:section.build-artifacts {:class (when show-artifacts "active")}
          [:div.build-artifacts-title
           [:strong "Build Artifacts"]
           [:a {:role "button"
                :on-click #(put! controls-ch [:show-artifacts-toggled])}
            [:span " view "]
            [:i.fa.fa-caret-down {:class (when show-artifacts "fa-rotate-180")}]]]
          (when show-artifacts
            (if-not artifacts
              [:div.loading-spinner common/spinner]

              [:ol.build-artifacts-list
               (map (fn [artifact]
                      [:li
                       (if admin?
                         ;; Be extra careful about XSS of admins
                         (cleanup-artifact-path (:pretty_path artifact))

                         [:a {:href (:url artifact) :target "_blank"}
                          (cleanup-artifact-path (:pretty_path artifact))])])
                    artifacts)]))])))))

(defn build-head [data owner]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch (om/get-shared owner [:comms :controls])
            build-data (:build-data data)
            build (:build build-data)
            build-id (build-model/id build)
            build-num (:build_num build)
            vcs-url (:vcs_url build)
            usage-queue-data (:usage-queue-data build-data)
            run-queued? (build-model/in-run-queue? build)
            usage-queued? (build-model/in-usage-queue? build)
            plan (get-in data [:project-data :plan])
            user (:user data)]
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
               [:td (when (:start_time build)
                      (list
                       (om/build common/updating-duration {:start (:start_time build)} {:opts {:formatter datetime/time-ago}}) " ago"))]]
              [:tr
               [:th "Trigger"]
               [:td (build-model/why-in-words build)]
               [:th "Duration"]
               [:td (if (build-model/running? build)
                      (om/build common/updating-duration {:start (:start_time build)
                                                          :stop (:stop_time build)})
                      (build-model/duration build))]]
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
                       [:td (if (< 0 (build-model/run-queued-time build))
                              [:span
                               (om/build common/updating-duration {:start (:usage_queued_at build)
                                                                   :stop (or (:queued_at build) (:stop_time build))})
                               " waiting + "
                               (om/build common/updating-duration {:start (:queued_at build)
                                                                   :stop (or (:start_time build) (:stop_time build))})
                               " in queue"]

                              [:span
                               (om/build common/updating-duration {:start (:usage_queued_at build)
                                                                   :stop (or (:queued_at build) (:stop_time build))})
                               " waiting for builds to finish"])

                        [:a#queued_explanation
                         {:on-click #(put! controls-ch [:usage-queue-why-toggled
                                                        {:build-id build-id
                                                         :username (:username @build)
                                                         :reponame (:reponame @build)
                                                         :build_num (:build_num @build)}])}
                         " view "]
                        [:i.fa.fa-caret-down {:class (when (:show-usage-queue usage-queue-data) "fa-rotate-180")}]]))
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
             [:div.actions
              (forms/stateful-button
               [:button.retry_build
                {:data-loading-text "Rebuilding",
                 :title "Retry the same tests",
                 :on-click #(put! controls-ch [:retry-build-clicked {:build-id build-id
                                                                     :vcs-url vcs-url
                                                                     :build-num build-num
                                                                     :clear-cache? false}])}
                "Rebuild"])
              (forms/stateful-button
               [:button.clear_cache_retry
                {:data-loading-text "Rebuilding",
                 :title "Clear cache and retry",
                 :on-click #(put! controls-ch [:retry-build-clicked {:build-id build-id
                                                                     :vcs-url vcs-url
                                                                     :build-num build-num
                                                                     :clear-cache? true}])}
                "& clear cache"])

              (forms/stateful-button
               [:button.ssh_build
                {:data-loading-text "Rebuilding",
                 :title "Retry with SSH in VM",
                 :on-click #(put! controls-ch [:ssh-build-clicked {:build-id build-id
                                                                   :vcs-url vcs-url
                                                                   :build-num build-num}])}
                "& enable ssh"])]
             [:div.actions
              [:button.report_build
               {:title "Report error with build",
                :on-click #(put! controls-ch [:report-build-clicked {:build-url (:build_url @build)}])}
               "Report"]
              (when (build-model/can-cancel? build)
                (forms/stateful-button
                 [:button.cancel_build
                  {:data-loading-text "Canceling",
                   :title "Cancel this build",
                   :on-click #(put! controls-ch [:cancel-build-clicked {:build-id build-id
                                                                        :vcs-url vcs-url
                                                                        :build-num build-num}])}
                  "Cancel"]))]]]
           (when (:show-usage-queue usage-queue-data)
             (om/build build-queue {:build build
                                    :builds (:builds usage-queue-data)
                                    :plan plan}))
           (when (:subject build)
             (om/build build-commits build))
           (when (build-model/ssh-enabled-now? build)
             (om/build build-ssh (:node build)))
           (when (:has_artifacts build)
             (om/build build-artifacts-list {:artifacts-data (get build-data :artifacts-data)
                                             :user user}))]])))))
