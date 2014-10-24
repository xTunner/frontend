(ns frontend.components.build-head
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as string]
            [frontend.async :refer [raise!]]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.models.test :as test-model]
            [frontend.components.builds-table :as builds-table]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.routes :as routes]
            [frontend.timer :as timer]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring]
            [goog.string.format]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

;; This is awful, can't we just pass build-head the whole app state?
;; splitting it up this way means special purpose paths to find stuff
;; in it depending on what sub-state with special keys we have, right?
(defn has-scope [scope data]
  (scope (:scopes data)))

(defn build-queue [data owner]
  (reify
    om/IRender
    (render [_]
      (let [{:keys [build builds]} data
            run-queued? (build-model/in-run-queue? build)
            usage-queued? (build-model/in-usage-queue? build)
            plan (:plan data)]
        (html
         (if-not builds
           [:div.loading-spinner common/spinner]
           [:div.build-queue.active
            (when (and (:queued_at build) (not usage-queued?))
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

(defn linkify [text]
  (let [url-pattern #"(?im)(\b(https?|ftp)://[-A-Za-z0-9+@#/%?=~_|!:,.;]*[-A-Za-z0-9+@#/%=~_|])"
        pseudo-url-pattern #"(?im)(^|[^/])(www\.[\S]+(\b|$))"]
    (-> text
        ;; TODO: switch to clojure.string/replace when they fix
        ;; http://dev.clojure.org/jira/browse/CLJS-485...
        (.replace (js/RegExp. (.-source url-pattern) "gim")
                  "<a href=\"$1\" target=\"_blank\">$1</a>")
        (.replace (js/RegExp. (.-source pseudo-url-pattern) "gim")
                  "$1<a href=\"http://$2\" target=\"_blank\">$2</a>"))))

(defn maybe-project-linkify [text project-name]
  (if-not project-name
    text
    (let [issue-pattern #"(^|\s)#(\d+)\b"]
      (-> text
          (string/replace issue-pattern
                          (gstring/format "$1<a href='https://github.com/%s/issues/$2' target='_blank'>#$2</a>" project-name))))))

(defn commit-line [{:keys [build subject body commit_url commit] :as commit-details} owner]
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
                :id (str "commit-line-tooltip-hack-" commit)
                :dangerouslySetInnerHTML {:__html (-> subject
                                                      (gstring/htmlEscape)
                                                      (linkify)
                                                      (maybe-project-linkify (vcs-url/project-name (:vcs_url build))))}}]
        [:a.sha-one {:href commit_url
                     :title commit}
         " "
         (subs commit 0 7)
         " "
         [:i.fa.fa-github]]]))))

(defn build-commits [build-data owner]
  (reify
    om/IRender
    (render [_]
      (let [build (:build build-data)
            build-id (build-model/id build)]
        (html
         [:div.build-commits-container
          [:div.build-commits-title
           (when (:compare build)
             [:a {:href (:compare build)}
              "compare "
              [:i.fa.fa-github]
              " "])
           (when (< 3 (count (:all_commit_details build)))
             [:a {:role "button"
                  :on-click #(raise! owner [:show-all-commits-toggled {:build-id build-id}])}
              (str (- (count (:all_commit_details build)) 3) " more ")
              (if (:show-all-commits build-data)
                [:i.fa.fa-caret-up]
                [:i.fa.fa-caret-down])])]
          [:div.build-commits-list
           (if-not (seq (:all_commit_details build))
             (om/build commit-line {:build build
                                    :subject (:subject build)
                                    :body (:body build)
                                    :commit_url (build-model/github-commit-url build)
                                    :commit (:vcs_revision build)})
             (list
              (om/build-all commit-line (take 3 (map #(assoc % :build build)
                                                     (:all_commit_details build))))
              (when (:show-all-commits build-data)
                (om/build-all commit-line (drop 3 (map #(assoc % :build build)
                                                       (:all_commit_details build)))))))]])))))

(defn ssh-ad [build owner]
  (let [build-id (build-model/id build)
        vcs-url (:vcs_url build)
        build-num (:build_num build)]
    [:div.ssh-ad
     [:p "Often the best way to troubleshoot problems is to ssh into a running or finished build to look at log files, running processes, and so on."]
     (forms/stateful-button
      [:button.ssh_build
       {:data-loading-text "Starting SSH build...",
        :title "Retry with SSH in VM",
        :on-click #(raise! owner [:ssh-build-clicked {:build-id build-id
                                                      :vcs-url vcs-url
                                                      :build-num build-num}])}
       "Retry this build with SSH enabled"])
     [:p "More information " [:a {:href (routes/v1-doc-subpage {:subpage "ssh-build"})} "in our docs"] "."]]))

(defn build-ssh [build owner]
  (reify
    om/IRender
    (render [_]
      (let [nodes (:node build)]
        (html
         (if-not (build-model/ssh-enabled-now? build)
           (ssh-ad build owner)
           [:div.ssh-info-container
            [:div.build-ssh-title
             [:p "You can SSH into this build. Use the same SSH public key that you use for GitHub. SSH boxes will stay up for 30 minutes."]
             [:p "This build takes up one of your concurrent builds, so cancel it when you are done."]]
            [:div.build-ssh-list
             [:dl.dl-horizontal
              (map (fn [node i]
                     (list
                      [:dt (when (< 1 (count nodes)) [:span (str "container " i " ")])]
                      [:dd {:class (when (:ssh_enabled node) "connected")}
                       [:span (gstring/format "ssh -p %s %s@%s " (:port node) (:username node) (:public_ip_addr node))]
                       (when-not (:ssh_enabled node)
                         [:span.loading-spinner common/spinner])]))
                   nodes (range))]]
            [:div.build-ssh-doc
             "Debugging Selenium browser tests? "
             [:a {:href "/docs/browser-debugging#interact-with-the-browser-over-vnc"}
              "Read our doc on interacting with the browser over VNC"]
             "."]]))))))

(defn cleanup-artifact-path [path]
  (-> path
      (string/replace "$CIRCLE_ARTIFACTS/" "")
      (gstring/truncateMiddle 80)))

(defn artifacts-ad []
  [:div
   [:p "We didn't find any build artifacts for this build. You can upload build artifacts by moving files to the $CIRCLE_ARTIFACTS directory."]
   [:p "Use artifacts for screenshots, coverage reports, deployment tarballs, and more."]
   [:p "More information " [:a {:href (routes/v1-doc-subpage {:subpage "build-artifacts"})} "in our docs"] "."]])

(defn build-artifacts-list [data owner {:keys [show-node-indices?] :as opts}]
  (reify
    om/IRender
    (render [_]
      (let [artifacts-data (:artifacts-data data)
            artifacts (:artifacts artifacts-data)
            has-artifacts? (:has-artifacts? data)
            admin? (:admin (:user data))]
        (html
         [:div.build-artifacts-container
          (if-not has-artifacts?
            (artifacts-ad)
            (if-not artifacts
              [:div.loading-spinner common/spinner]

              [:ol.build-artifacts-list
               (map (fn [artifact]
                      (let [display-path (-> artifact
                                             :pretty_path
                                             cleanup-artifact-path
                                             (str (when show-node-indices? (str " (" (:node_index artifact) ")"))))]
                        [:li
                         (if admin? ; Be extra careful about XSS of admins
                           display-path
                           [:a {:href (:url artifact) :target "_blank"} display-path])]))
                    artifacts)]))])))))

(defn tests-ad [owner]
  [:div
   [:p "We didn't find any test metadata for this build. If you're using our inferred RSpec or Cucumber test steps, then we'll collect the metadata automatically. RSpec users will also have add our junit formatter gem to their Gemfile, with the line:"]
   [:p [:code "gem 'rspec_junit_formatter', :git => 'git@github.com:circleci/rspec_junit_formatter.git'"]]
   [:p [:a {:on-click #(raise! owner [:intercom-dialog-raised])} "Let us know"]
    " if you want us to add support for your test runner."]])

(defn build-tests-list [data owner]
  (reify
    om/IRender
    (render [_]
      (let [tests-data (:tests-data data)
            tests (:tests tests-data)
            sources (reduce (fn [s test] (conj s (:source test))) #{} tests)
            failed-tests (filter #(not= "success" (:result %)) tests)]
        (html
         [:div.build-tests-container
          (if-not tests
            [:div.loading-spinner common/spinner]
            (if (empty? tests)
              (tests-ad owner)
              [:div.build-tests-info
               [:div.build-tests-summary
                (str "Your build ran " (count tests) " tests in "
                     (string/join ", " (map test-model/pretty-source sources))
                     " with ")
                [:span.failed-count (str (count failed-tests))]
                " failures."]
               (when (seq failed-tests)
                 (for [[source tests] (group-by :source failed-tests)]
                   [:div.build-tests-list-container
                    [:span.failure-source (str (test-model/pretty-source source) " failures:")]
                    [:ol.build-tests-list
                     (map (fn [test]
                            [:li (test-model/format-test-name test)])
                          (sort-by test-model/format-test-name tests))]]))]))])))))

(defn circle-yml-ad []
  [:div
   [:p "We didn't find a circle.yml for this build. You can specify deployment or override our inferred test steps from a circle.yml checked in to your repo's root directory."]
   [:p "More information " [:a {:href (routes/v1-doc-subpage {:subpage "configuration"})} "in our docs"] "."]])

(defn build-config [{:keys [config-string]} owner opts]
  (reify
    om/IRender
    (render [_]
      (html
       (if (seq config-string)
         [:div.build-config-string [:pre config-string]]
         (circle-yml-ad))))))

(defn expected-duration
  [{:keys [start stop build]} owner opts]
  (reify

    om/IDisplayName
    (display-name [_] "Expected Duration")

    om/IDidMount
    (did-mount [_]
      (timer/set-updating! owner (not stop)))

    om/IDidUpdate
    (did-update [_ _ _]
      (timer/set-updating! owner (not stop)))

    om/IRender
    (render [_]
      (let [end-ms (if stop
                     (.getTime (js/Date. stop))
                     (datetime/server-now))
            formatter (get opts :formatter datetime/as-duration)
            duration-ms (- end-ms (.getTime (js/Date. start)))
            previous-build (:previous_successful_build build)
            past-ms (:build_time_millis previous-build)]
        (if (and past-ms
                 (= (:status build) "running")
                 (< duration-ms (* 1.5 past-ms)))
          (dom/span nil "/~" (formatter past-ms))
          (dom/span nil ""))))))

(defn build-sub-head [data owner]
  (reify
    om/IRender
    (render [_]
      (let [build-data (:build-data data)
            user (:user data)
            logged-in? (not (empty? user))
            build (:build build-data)
            show-ssh-info? (and (has-scope :write-settings data) (build-model/ssh-enabled-now? build))
            ;; default to ssh-info for SSH builds if they haven't clicked a different tab
            selected-tab (get build-data :selected-header-tab (if show-ssh-info? :ssh-info :commits))
            build-id (build-model/id build)
            build-num (:build_num build)
            vcs-url (:vcs_url build)
            usage-queue-data (:usage-queue-data build-data)
            run-queued? (build-model/in-run-queue? build)
            usage-queued? (build-model/in-usage-queue? build)
            plan (get-in data [:project-data :plan])
            config-data (:config-data build-data)]
        (html
         [:div.sub-head
          [:div.sub-head-top
           [:ul.nav.nav-tabs
            (when (:subject build)
              [:li {:class (when (= :commits selected-tab) "active")}
               [:a {:on-click #(raise! owner [:build-header-tab-clicked {:tab :commits}])}
                "Commit Log"]])

            [:li {:class (when (= :config selected-tab) "active")}
             [:a {:on-click #(raise! owner [:build-header-tab-clicked {:tab :config}])}
              "circle.yml"]]

            (when logged-in?
              [:li {:class (when (= :usage-queue selected-tab) "active")}
               [:a {:on-click #(do (raise! owner [:build-header-tab-clicked {:tab :usage-queue}])
                                   (raise! owner [:usage-queue-why-showed
                                                  {:build-id build-id
                                                   :username (:username @build)
                                                   :reponame (:reponame @build)
                                                   :build_num (:build_num @build)}]))}
                "Queue"
                (when (:usage_queued_at build)
                  [:span " ("
                   (om/build common/updating-duration {:start (:usage_queued_at build)
                                                       :stop (or (:start_time build) (:stop_time build))})
                   ")"])]])

            (when (has-scope :write-settings data)
              [:li {:class (when (= :ssh-info selected-tab) "active")}
               [:a {:on-click #(raise! owner [:build-header-tab-clicked {:tab :ssh-info}])}
                "SSH info"]])

            ;; tests don't get saved until the end of the build (TODO: stream the tests!)
            (when (build-model/finished? build)
              [:li {:class (when (= :tests selected-tab) "active")}
               [:a {:on-click #(do
                                 (raise! owner [:build-header-tab-clicked {:tab :tests}])
                                 (raise! owner [:tests-showed]))}
                "Test Failures"]])

            ;; artifacts don't get uploaded until the end of the build (TODO: stream artifacts!)
            (when (and logged-in? (build-model/finished? build))
              [:li {:class (when (= :artifacts selected-tab) "active")}
               [:a {:on-click #(do (raise! owner [:build-header-tab-clicked {:tab :artifacts}])
                                   (raise! owner [:artifacts-showed]))}
                "Artifacts"]])]]

          [:div.sub-head-content
           (case selected-tab
             :commits (om/build build-commits build-data)

             :tests (om/build build-tests-list build-data)

             :artifacts (om/build build-artifacts-list
                                  {:artifacts-data (get build-data :artifacts-data) :user user
                                   :has-artifacts? (:has_artifacts build)}
                                  {:opts {:show-node-indices? (< 1 (:parallel build))}})

             :config (om/build build-config {:config-string (get-in build [:circle_yml :string])})

             :usage-queue (om/build build-queue {:build build
                                                 :builds (:builds usage-queue-data)
                                                 :plan plan})
             :ssh-info (om/build build-ssh build))]])))))

(defn build-head [data owner]
  (reify
    om/IRender
    (render [_]
      (let [build-data (:build-data data)
            build (:build build-data)
            build-id (build-model/id build)
            build-num (:build_num build)
            vcs-url (:vcs_url build)
            usage-queue-data (:usage-queue-data build-data)
            run-queued? (build-model/in-run-queue? build)
            usage-queued? (build-model/in-usage-queue? build)
            plan (get-in data [:project-data :plan])
            user (:user data)
            logged-in? (not (empty? user))
            config-data (:config-data build-data)]
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
                      {:title (datetime/full-datetime (:start_time build))})
                (when (:start_time build)
                  (list (om/build common/updating-duration
                                  {:start (:start_time build)}
                                  {:opts {:formatter datetime/time-ago}}) " ago"))]]
              [:tr
               [:th "Trigger"]
               [:td (build-model/why-in-words build)]

               [:th "Duration"]
               [:td (if (build-model/running? build)
                      (om/build common/updating-duration {:start (:start_time build)
                                                          :stop (:stop_time build)})
                      (build-model/duration build))
                    (om/build expected-duration {:start (:start_time build)
                                                :stop (:stop_time build)
                                                :build build})]]
              [:tr
               [:th "Previous"]
               (if-not (:previous build)
                 [:td "none"]
                 [:td
                  [:a {:href (routes/v1-build-path (vcs-url/org-name vcs-url) (vcs-url/repo-name vcs-url) (:build_num (:previous build)))}
                   (:build_num (:previous build))]])
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
                               " waiting for builds to finish"])]))
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
                (if (has-scope :write-settings data)
                  [:a {:title (str "This build used " (:parallel build) " containers. Click here to change parallelism for future builds.")
                       :href (build-model/path-for-parallelism build)}
                   (str (:parallel build) "x")]
                  [:span (:parallel build) "x"])]
               (when-let [urls (seq (:pull_request_urls build))]
                 ;; It's possible for a build to be part of multiple PRs, but it's rare
                 (list [:th (str "PR" (when (< 1 (count urls)) "s"))]
                       [:td
                        (interpose
                         ", "
                         (map (fn [url] [:a {:href url} "#"
                                         (let [n (re-find #"/\d+$" url)]
                                           (if n (subs n 1) "?"))])
                              urls))]))]]]

            [:div.build-actions
             [:div.actions
              (forms/stateful-button
               [:button.retry_build
                {:data-loading-text "Rebuilding",
                 :title "Retry the same tests",
                 :on-click #(raise! owner [:retry-build-clicked {:build-id build-id
                                                                 :vcs-url vcs-url
                                                                 :build-num build-num
                                                                 :clear-cache? false}])}
                "Rebuild"])

              (forms/stateful-button
               [:button.clear_cache_retry
                {:data-loading-text "Rebuilding",
                 :title "Clear cache and retry",
                 :on-click #(raise! owner [:retry-build-clicked {:build-id build-id
                                                                 :vcs-url vcs-url
                                                                 :build-num build-num
                                                                 :clear-cache? true}])}
                "& clear cache"])

              (if (has-scope :write-settings data)
                (forms/stateful-button
                 [:button.ssh_build
                  {:data-loading-text "Rebuilding",
                   :title "Retry with SSH in VM",
                   :on-click #(raise! owner [:ssh-build-clicked {:build-id build-id
                                                                 :vcs-url vcs-url
                                                                 :build-num build-num}])}
                  "& enable ssh"]))]
             [:div.actions
              (when logged-in? ;; no intercom for logged-out users
                [:button.report_build
                 {:title "Report error with build",
                  :on-click #(raise! owner [:report-build-clicked {:build-url (:build_url @build)}])}
                 "Report"])
              (when (build-model/can-cancel? build)
                (forms/stateful-button
                 [:button.cancel_build
                  {:data-loading-text "Canceling",
                   :title "Cancel this build",
                   :on-click #(raise! owner [:cancel-build-clicked {:build-id build-id
                                                                    :vcs-url vcs-url
                                                                    :build-num build-num}])}
                  "Cancel"]))]]
            [:div.no-user-actions]]

           (om/build build-sub-head data)]])))))
