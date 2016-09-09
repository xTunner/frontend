(ns frontend.components.build-head
  (:require [clojure.string :as string]
            [frontend.async :refer [navigate! raise!]]
            [frontend.components.build-config :as build-cfg]
            [frontend.components.build-timings :as build-timings]
            [frontend.components.builds-table :as builds-table]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.tabs :as tabs]
            [frontend.components.svg :refer [svg]]
            [frontend.config :refer [enterprise? github-endpoint]]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.models.plan :as plan-model]
            [frontend.models.project :as project-model]
            [frontend.models.test :as test-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.build :as build-util]
            [frontend.utils.bitbucket :as bb-utils]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.html :refer [open-ext]]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring]
            [inflections.core :refer [pluralize]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]))

;; This is awful, can't we just pass build-head the whole app state?
;; splitting it up this way means special purpose paths to find stuff
;; in it depending on what sub-state with special keys we have, right?
(defn has-scope [scope data]
  (scope (:scopes data)))

(defn show-additional-containers-offer? [plan build]
  (when (and plan build (not (enterprise?)))
    (let [usage-queued-ms (build-model/usage-queued-time build)
          run-queued-ms (build-model/run-queued-time build)]
      ;; more than 10 seconds waiting for other builds, and
      ;; less than 10 seconds waiting for additional containers (our fault)
      (< run-queued-ms 10000 usage-queued-ms))))

(defn new-additional-containers-offer [{{plan-org-name :name
                                         plan-vcs-type :vcs_type} :org
                                        :as plan}
                                       build]
  (let [run-phrase (if (build-model/finished? build)
                     "ran"
                     "is running")]
    [:div.additional-containers-offer
     [:p
      "This build "
      run-phrase
      " under "
      plan-org-name
      "'s plan which provides "
      (plan-model/linux-containers plan)
      " containers, plus 3 additional containers available for free and open source projects. "
      [:a {:href (routes/v1-org-settings-path {:org plan-org-name
                                               :vcs_type plan-vcs-type
                                               :_fragment "linux-pricing"})}
       "Add Containers"]
      " to run more builds concurrently."]]))

(defn queued-explanation-text []
  (if (not (enterprise?))
    " We're sorry; this is our fault. Typically you should only see this when load spikes overwhelm our auto-scaling; waiting to acquire containers should be brief and infrequent."
    [:span
     " Typically, this means you have more builds scheduled to be run than available capacity in the fleet. "
     (if (:admin (utils/current-user))
       [:span "Check " [:a {:href "/admin/fleet-state"} "Fleet State"] " for details and potentially start new builders"]
       "Ask a CircleCI Enterprise administrator to check fleet state and launch new builder machines")]))

(defn build-queue [data owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [{:keys [build]} data
            build-id (build-model/id build)]
        (raise! owner [:usage-queue-why-showed
                       {:build-id build-id
                        :username (:username @build)
                        :reponame (:reponame @build)
                        :build_num (:build_num @build)}])))

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
            [:div.queue-message
             (when (and (:queued_at build) (not usage-queued?))
               (list
                 "CircleCI " (when run-queued? "has") " spent "
                 [:strong
                  (om/build common/updating-duration {:start (:queued_at build)
                                                      :stop (or (:start_time build) (:stop_time build))})]
                 " acquiring containers for this build."))

             (when (< 10000 (build-model/run-queued-time build))
               [:span#circle_queued_explanation
                (queued-explanation-text)])
             (when (seq builds)
               [:span
                " This build " (if usage-queued? "has been" "was")
                " queued behind the following builds for "
                [:strong
                 (om/build common/updating-duration {:start (:usage_queued_at build)
                                                     :stop (or (:queued_at build) (:stop_time build))})]
                (when (show-additional-containers-offer? plan build)
                  (new-additional-containers-offer plan build))])]

              (when (seq builds)
               [:div.queued-builds
                (om/build builds-table/builds-table builds {:opts {:show-actions? true}})])]))))))

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
                          (gstring/format "$1<a href='%s/%s/issues/$2' target='_blank'>#$2</a>" (gh-utils/http-endpoint) project-name))))))

(defn ssh-enabled-note
  "Note that SSH has been enabled for the build, with list of users"
  [[current-user? someone-else?] owner]
  (reify
    om/IRender
    (render [_]
      (html [:p (->> [(when current-user? "you")
                      (when someone-else? "someone other than you")]
                     (filter identity)
                     (string/join " and ")
                     (string/capitalize)
                     (#(str % " enabled SSH for this build.")))]))))

(defn ssh-buttons
  "Show the enable-SSH button(s) for the SSH tab

  Includes the button to SSH to the current build if it is still running, and
  the current user hasn't already enabled SSH for themselves; always shows the
  button to rebuild with SSH enabled (for the current user.)

  Assumes that the user has permission to SSH to the build (=> these should be
  shown)"
  [build owner]
  (reify
    om/IRender
    (render [_]
      (let [build-info {:build-id (build-model/id build)
                        :vcs-url (:vcs_url build)
                        :build-num (:build_num build)}]
        (html
          [:div
           (if-not (build-model/finished? build)
             (forms/managed-button
               [:button.ssh_build
                {:data-loading-text "Adding your SSH keys..."
                 :title "Enable SSH for this build"
                 :on-click #(raise! owner [:ssh-current-build-clicked build-info])}
                "Enable SSH for this build"])
             (forms/managed-button
               [:button.ssh_build
                {:data-loading-text "Starting SSH build..."
                 :title "Retry with SSH in VM"
                 :on-click #(raise! owner [:ssh-build-clicked build-info])}
                "Retry this build with SSH enabled"]))])))))

(defn ssh-ad
  "Note about why you might want to SSH into a build and buttons to do so"
  [build owner]
    [:div.ssh-ad
     [:p
      "Often the best way to troubleshoot problems is to SSH into a running or finished build to look at log files, running processes, and so on.
       This will grant you ssh access to the build's containers, prevent the deploy step from starting, and keep the build up for 30 minutes after it finishes to give you time to investigate.
       More information " [:a (open-ext {:href "https://circleci.com/docs/ssh-build/"}) "in our docs"] "."
     (om/build ssh-buttons build)]])

(defn ssh-command [node]
  (gstring/format "ssh -p %s %s@%s " (:port node) (:username node) (:public_ip_addr node)))

(defrender ssh-node-list [nodes owner]
  (html
    [:ul.ssh-nodes-list
     (map-indexed (fn [i node]
                    (let [no-ssh?       (not (:ssh_enabled node))
                          command-class (cond-> "ssh-node-command"
                                          no-ssh? (str " ssh-node-disabled"))]
                      [:li.ssh-node
                       [:span.ssh-node-container (str "Container " i)]
                       [:span {:class command-class} (ssh-command node)]
                       (when no-ssh?
                         (om/build svg {:class "ssh-node-running-icon" :src (common/icon-path "Status-Running")}))]))
                  nodes)]))

(defn ssh-instructions
  "Instructions for SSHing into a build that you can SSH into"
  [{:keys [vcs_type] :as build} owner]
  (let [nodes (:node build)]
    (html
     [:div.ssh-info-container
      [:div.build-ssh-title
       [:p (gstring/format "You can SSH into this build using a key associated with your %s account. Hosts will stay up for 30 minutes."
                           (utils/prettify-vcs_type vcs_type))]
       [:div
        "This build takes up one of your concurrent builds, so cancel it when you are done. Browser based testing? Read "
        [:a (open-ext {:href "https://circleci.com/docs/browser-debugging#interact-with-the-browser-over-vnc/"}) "our docs"]
        " on how to use VNC with CircleCI."]]

      (om/build ssh-node-list nodes)])))

(defn build-ssh [{:keys [build user]} owner]
  (reify
    om/IRender
    (render [_]
      (let [for-current-user? (build-model/current-user-ssh? build user)
            for-someone-else? (build-model/someone-else-ssh? build user)]
        (html
          [:div
           (when (seq (:ssh_users build))
             (om/build ssh-enabled-note [for-current-user? for-someone-else?]))
           (if for-current-user?
             (cond
               (build-model/ssh-enabled-now? build) (ssh-instructions build owner)
               (build-model/finished? build) (ssh-ad build owner))
             (ssh-ad build owner))])))))

(defn cleanup-artifact-path [path]
  (-> path
      (string/replace "$CIRCLE_ARTIFACTS/" "")
      (gstring/truncateMiddle 80)))

(defn artifacts-ad []
  [:div
   [:p "We didn't find any build artifacts for this build. You can upload build artifacts by moving files to the $CIRCLE_ARTIFACTS directory."]
   [:p "Use artifacts for screenshots, coverage reports, deployment tarballs, and more."]
   [:p "More information " [:a (open-ext {:href "https://circleci.com/docs/build-artifacts/"}) "in our docs"] "."]])

(defn artifacts-tree [prefix artifacts]
  (->> (for [artifact artifacts
             :let [parts (concat [prefix]
                                 (-> (:pretty_path artifact)
                                     (string/split #"/")))]]
         [(vec (remove #{""} parts)) artifact])
       (reduce (fn [acc [parts artifact]]
                 (let [loc (interleave (repeat :children) parts)]
                   (assoc-in acc (concat loc [:artifact]) artifact)))
               {})
       :children))

(defn artifacts-node [{:keys [depth artifacts] :as data} owner opts]
  (reify
    om/IRender
    (render [_]
      (html
       (when (seq artifacts)
         [:ul.build-artifacts-list
          (map-indexed
           (fn node-entry [idx [part {:keys [artifact children]}]]
             (let [directory? (not artifact)
                   text       (if (and (not= 0 depth) directory?)
                                (str part "/")
                                part)
                   url        (:url artifact)
                   tag        (if url
                                [:a.artifact-link {:href (:url artifact) :target "_blank"} text]
                                [:span.artifact-directory-text text])
                   key        (keyword (str "index-" idx))
                   key-state  (om/get-state owner [key])
                   closed?    (or
                               (:ancestors-closed? opts)
                               (if (contains? key-state :closed?)
                                 (:closed? key-state)
                                 (> depth 1)))
                   toggler    (fn [event]
                                (let [key (keyword (str "index-" idx))]
                                  (.preventDefault event)
                                  (.stopPropagation event)
                                  (om/set-state! owner [key :closed?] (not closed?))))]
               [:li.build-artifacts-node {:class (when (= 0 depth) "container-artifacts")}
                (if directory?
                  [:div.build-artifacts-toggle-children
                   {:style    {:cursor  "pointer"
                               :display "inline"}
                    :on-click toggler}
                   [:i.fa.artifact-toggle-caret
                    {:class (if closed? "fa-angle-right" "fa-angle-down")}]
                   " "
                   tag]
                  tag)
                [:div {:style (when closed? {:display "none"})}
                 (om/build artifacts-node
                           {:depth (+ depth 1)
                            :artifacts children}
                           {:opts (assoc opts
                                    :ancestors-closed? (or (:ancestors-closed? opts) closed?))})]]))
           (sort-by first artifacts))])))))

(defn build-artifacts-list [data owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (raise! owner [:artifacts-showed]))

    om/IRender
    (render [_]
      (let [artifacts-data (:artifacts-data data)
            artifacts (:artifacts artifacts-data)
            has-artifacts? (:has-artifacts? data)]
        (html
         [:div.build-artifacts-container
          (if-not has-artifacts?
            (artifacts-ad)
            (if artifacts
              (interpose [:hr]
                         (map (fn artifact-node-builder [[node-index node-artifacts]]
                                (om/build artifacts-node {:artifacts (artifacts-tree (str "Container " node-index) node-artifacts)
                                                          :depth 0}))
                              (->> artifacts
                                   (group-by :node_index)
                                   (sort-by first))))
              [:div.loading-spinner common/spinner]))])))))

(defn tests-ad [owner language]
  [:div
   "Help us provide better insight around your tests and failures. "
   [:a (open-ext {:href (case language
                          "Clojure" "https://circleci.com/docs/test-metadata/#test2junit-for-clojure-tests"
                          "Ruby" "https://circleci.com/docs/test-metadata/#rspec"
                          "JavaScript" "https://circleci.com/docs/test-metadata/#js"
                          "Python" "https://circleci.com/docs/test-metadata#python"
                          "Java" "https://circleci.com/docs/test-metadata#java-junit-results-with-maven-surefire-plugin"
                          "https://circleci.com/docs/test-metadata/#metadata-collection-in-custom-test-steps")
                  :on-mouse-up #((om/get-shared owner :track-event) {:event-type :set-up-junit-clicked
                                                                     :properties {:language language}})})
    "Set up your test runner to output in JUnit-style XML"] ", so we can:"
   [:ul
    [:li "Show a summary of all test failures across all containers"]
    [:li "Identify your slowest tests"]
    [:li [:a (open-ext {:href "https://circleci.com/docs/parallel-manual-setup/"}) "Balance tests between containers when using properly configured parallelization"]]]])

(defrender parse-errors [exceptions owner]
  (html
   [:div
    "The following errors were encountered parsing test results:"
    [:dl
     (for [[file exception] exceptions]
       (list
        [:dt [:a {:href "#artifacts"} file]]
        [:dd exception]))]]))

(defmulti format-test-name test-model/source)

(defmethod format-test-name :default [test]
  (->> [[(:name test)] [(:classname test)]]
       (map (fn [s] (some #(when-not (string/blank? %) %) s)))
       (filter identity)
       (string/join " - ")))

(defmethod format-test-name "lein-test" [test]
  (str (:classname test) "/" (:name test)))

(defmethod format-test-name "cucumber" [test]
  (if (string/blank? (:name test))
    (:classname test)
    (:name test)))

(defn test-item [test owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:li.build-test
        [:div.properties
         [:div.test-name (format-test-name test)]
         [:div.test-file (:file test)]]
        (let [message (or (:message test) "")
              display-message (if (:show-message test)
                                message
                                (second (re-find #"(?m)^\s*(.*)$" message)))
              expander-label (if (:show-message test) "less" "more")
              multiple-lines? (re-find #"\n|\r\n" message)]
          [:pre.build-test-output
           (when multiple-lines?
             [:div.expander {:role "button"
                             :on-click #(raise! owner [:show-test-message-toggled {:test-index (:i test)}])}
              expander-label])
           (if (empty? message)
             "(no output)"
             display-message)])]))))

(def initial-test-render-count 3)

(defn build-tests-file-block [[file failures] owner]
  (reify om/IRender
    (render [_]
      (html
       [:div
        (om/build-all test-item (vec failures))]))))

(defn build-tests-source-block [[source {:keys [failures successes]}] owner]
  (reify om/IRender
    (render [_]
      (html
       [:div.alert.alert-danger.expanded.build-tests-info
        [:div.alert-header
         [:img.alert-icon {:src (common/icon-path "Info-Error")}]
         [:span.source-name (test-model/pretty-source source)]
         [:span.failure-count (pluralize (count failures) "failure")]]
        [:div.alert-body
         [:div.build-tests-summary
          "Your build ran "
          [:strong (pluralize (+ (count failures)
                                 (count successes)) "test")]
          " with "
          [:strong (pluralize (count failures) "failure")]]
         [:div.build-tests-list-container
          [:ol.list-unstyled.build-tests-list
           (let [file-failures (->> (group-by :file failures)
                                    (map (fn [[k v]] [k (sort-by test-model/format-test-name v)]))
                                    (into (sorted-map)))
                 [top-map bottom-map] (utils/split-map-values-at file-failures initial-test-render-count)]
             (list
              (om/build-all build-tests-file-block top-map)
              (when-not (empty? bottom-map)
                (list
                 [:hr]
                 [:li
                  [:a.build-tests-toggle {:on-click #(om/update-state! owner [:is-open?] not)}
                   [:span
                    [:i.fa.build-tests-toggle-icon {:class (if (om/get-state owner :is-open?) "expanded")}]
                    (if (om/get-state owner :is-open?)
                      "Less"
                      "More")]]]
                 (when (om/get-state owner :is-open?)
                   (om/build-all build-tests-file-block bottom-map))))))]]]]))))

(defn build-tests-list [{project :project
                        {{:keys [tests exceptions]} :tests-data
                         {build-status :status} :build
                         :as data} :build-data}
                        owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (raise! owner [:tests-showed]))
    om/IRender
    (render [_]
      (let [source-hash (->> tests
                             (map-indexed #(assoc %2 :i %1))
                             (reduce (fn [acc {:keys [result] :as test}]
                                       (update-in acc [(test-model/source test)
                                                       (if (#{"failure" "error"} result)
                                                         :failures
                                                         :successes)]
                                                  #(cons test %)))
                                     {}))
            failed-sources (filter (fn [[_ {:keys [failures]}]]
                                     (seq failures))
                                   source-hash)
            build-succeeded? (= "success" build-status)

            by-time (reverse (sort-by :run_time (filter (comp number? :run_time)
                                                        tests)))
            slowest (first by-time)]
        (html
         [:div.test-results
          (if-not tests
            [:div.loading-spinner common/spinner]
            (list
             (when (> (count exceptions) 0)
               [:div.alert.iconified {:class "alert-danger" }
                [:div [:img.alert-icon {:src (common/icon-path
                                              "Info-Error")}]]
                (om/build parse-errors exceptions)])
             (cond
               (seq failed-sources) (om/build-all build-tests-source-block failed-sources)
               (seq tests) [:div
                            "Your build ran "
                            [:strong (count tests)]
                            " tests in " (string/join ", " (map test-model/pretty-source (keys source-hash))) " with "
                            [:strong "0 failures"]
                            (when slowest
                              [:div.build-tests-summary
                               [:p
                                [:strong "Slowest test:"]
                                (gstring/format
                                 " %s %s (took %.2f seconds)."
                                 (:classname slowest)
                                 (:name slowest)
                                 (:run_time slowest))]])]
               :else [:div.alert.iconified {:class "alert-info" }
                      [:div [:img.alert-icon {:src (common/icon-path
                                                    (if build-succeeded? "Info-Info" "Info-Error"))}]]
                      (tests-ad owner (:language project))])))])))))

(defn circle-yml-ad []
  [:div
   [:p "We didn't find a circle.yml for this build. You can specify deployment or override our inferred test steps from a circle.yml checked in to your repo's root directory."]
   [:p "More information " [:a (open-ext {:href "https://circleci.com/docs/configuration/"}) "in our docs"] "."]])

(defn build-config [{:keys [config-string build build-data]} owner opts]
  (reify
    om/IDidMount
    (did-mount [_]
      (let [node (om/get-node owner)
            highlight-target (goog.dom.getElementByClass "config-yml" node)]
        (js/Prism.highlightElement highlight-target)))
    om/IRender
    (render [_]
      (html
       (if (seq config-string)
         [:div
          (when (and (build-model/config-errors? build)
                     (not (:dismiss-config-errors build-data)))
            (om/build build-cfg/config-errors build))
          [:div.build-config-string [:pre.line-numbers
                                     [:code.config-yml.language-yaml config-string]]]]
         (circle-yml-ad))))))

(defn build-parameters [{:keys [build-parameters]} owner opts]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.build-parameters
        [:pre (for [[k v] build-parameters
                    :let [pname (name k) pval (pr-str v)]]
                (str pname "=" pval \newline))]]))))

(defn link-to-user [build]
  (when-let [user (:user build)]
    (if (= "none" (:login user))
      [:em "Unknown"]
      [:a {:href (vcs-url/profile-url user)}
       (build-model/ui-user build)])))

(defn link-to-commit [build]
  [:a {:href (:compare build)}
   (take 7 (:vcs_revision build))])

(defn link-to-retry-source [build]
  (when-let [retry-id (:retry_of build)]
    [:a {:href (routes/v1-build-path
                 (vcs-url/vcs-type (:vcs_url build))
                 (:username build)
                 (:reponame build)
                 retry-id)}
     retry-id]))

(defn trigger-html [build]
  (let [user-link (link-to-user build)
        commit-link (link-to-commit build)
        retry-link (link-to-retry-source build)
        cache? (build-model/dependency-cache? build)]
    (case (:why build)
      "github" (list user-link " (pushed " commit-link ")")
      "bitbucket" (list user-link " (pushed " commit-link ")")
      "edit" (list user-link " (updated project settings)")
      "first-build" (list user-link " (first build)")
      "retry"  (list user-link " (retried " retry-link
                                (when-not cache? " without cache")")")
      "ssh" (list user-link " (retried " retry-link " with SSH)")
      "auto-retry" (list "CircleCI (auto-retry of " retry-link ")")
      "trigger" (if (:user build)
                  (list user-link " on CircleCI.com")
                  (list "CircleCI.com"))
      "api" "API"
      (or
        (:job_name build)
        "unknown"))))

(defn commit-line [{:keys [author_name build subject body commit_url commit] :as commit-details} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (when (seq body)
        (utils/tooltip (str "#commit-line-tooltip-hack-" commit)
                       {:placement "bottom"
                        :animation false
                        :viewport ".build-commits-container"})))
    om/IRender
    (render [_]
      (html
       [:div.build-commits-list-item
        [:span.metadata-item
         (if-not (:author_email commit-details)
           [:span
            (build-model/author commit-details)]
           [:a {:href (str "mailto:" (:author_email commit-details))}
            (build-model/author commit-details)])]
        (when (build-model/author-isnt-committer commit-details)
          [:span.metadata-item
           (if-not (:committer_email commit-details)
             [:span
              (build-model/committer commit-details)]
             [:a {:href (str "mailto:" (:committer_email commit-details))}
              (build-model/committer commit-details)])])

        [:i.octicon.octicon-git-commit]
        [:a.metadata-item.sha-one {:href commit_url
                                   :title commit
                                   :on-click #((om/get-shared owner :track-event) {:event-type :build-head-revision-link-clicked})}
         (subs commit 0 7)]
        [:span.commit-message
         {:title body
          :id (str "commit-line-tooltip-hack-" commit)
          :dangerouslySetInnerHTML {:__html (-> subject
                                                (gstring/htmlEscape)
                                                (linkify)
                                                (maybe-project-linkify (vcs-url/project-name (:vcs_url build))))}}]]))))

(def initial-build-commits-count 3)

(defn build-commits [{:keys [build show-all-commits?]} owner]
  (reify
    om/IRender
    (render [_]
      (let [build-id (build-model/id build)
            commits (:all_commit_details build)
            [top-commits bottom-commits] (->> commits
                                             (map #(assoc % :build build))
                                             (split-at initial-build-commits-count))]
        (html
         [:div.build-commits-container
          (when (:subject build)
            [:div.build-commits-list
             (if-not (seq commits)
               (om/build commit-line {:build build
                                      :subject (:subject build)
                                      :body (:body build)
                                      :commit_url (build-model/commit-url build)
                                      :commit (:vcs_revision build)})
               (list
                 (om/build-all commit-line top-commits)
                 (when (< initial-build-commits-count (count commits))
                   (list
                     [:hr]
                     [:a {:class "chevron-label"
                          :role "button"
                          :on-click #(raise! owner [:show-all-commits-toggled])}
                      [:i.fa.rotating-chevron {:class (when show-all-commits? "expanded")}]
                      (if show-all-commits?
                        "Less"
                        "More")]))
                 (when show-all-commits?
                   (om/build-all commit-line bottom-commits))))])])))))

(defn build-sub-head [data owner]
  (reify
    om/IRender
    (render [_]
      (let [build-data (:build-data data)
            scopes (:scopes data)
            user (:user data)
            logged-in? (not (empty? user))
            admin? (:admin user)
            build (:build build-data)
            current-container-id (:current-container-id data)
            selected-tab-name (or (:current-tab data)
                                  (build-util/default-tab build scopes))
            build-id (build-model/id build)
            build-num (:build_num build)
            vcs-url (:vcs_url build)
            usage-queue-data (:usage-queue-data build-data)
            run-queued? (build-model/in-run-queue? build)
            usage-queued? (build-model/in-usage-queue? build)
            project (get-in data [:project-data :project])
            plan (get-in data [:project-data :plan])
            config-data (:config-data build-data)
            build-params (:build_parameters build)]
        (card/tabbed
         {:tab-row
          (om/build tabs/tab-row
                    {:tabs (cond-> []
                             ;; tests don't get saved until the end of the build (TODO: stream the tests!)
                             (build-model/finished? build)
                             (conj {:name :tests
                                    :label (str
                                            "Test Summary"
                                            (when-let [fail-count (some->> build-data
                                                                           :tests-data
                                                                           :tests
                                                                           (filter #(contains? #{"failure" "error"} (:result %)))
                                                                           count)]
                                              (when (not= 0 fail-count)
                                                (str " (" fail-count ")"))))})

                             (has-scope :read-settings data)
                             (conj {:name :usage-queue
                                    :label (html
                                            (list
                                             "Queue"
                                             (when (:usage_queued_at build)
                                               [:span " ("
                                                (om/build common/updating-duration {:start (:usage_queued_at build)
                                                                                    :stop (or (:start_time build) (:stop_time build))})
                                                ")"])))})

                             (and (has-scope :trigger-builds data)
                                  (:ssh-available? data)
                                  (not (:ssh_disabled build)))
                             (conj {:name :ssh-info :label "Debug via SSH"})

                             ;; artifacts don't get uploaded until the end of the build (TODO: stream artifacts!)
                             (and logged-in? (build-model/finished? build))
                             (conj {:name :artifacts :label "Artifacts"})

                             true
                             (conj {:name :config
                                    :label (str "circle.yml"
                                                (when-let [errors (-> build build-model/config-errors)]
                                                  (gstring/format " (%s)" (count errors))))})

                             (build-model/finished? build)
                             (conj {:name :build-timing :label "Build Timing"})

                             (seq build-params)
                             (conj {:name :build-parameters :label "Build Parameters"}))
                     :selected-tab-name selected-tab-name
                     :on-tab-click #(do
                                      (navigate! owner (routes/v1-build-path
                                                         (vcs-url/vcs-type (:vcs_url build))
                                                         (:username build)
                                                         (:reponame build)
                                                         (:build_num build)
                                                         (name %)
                                                         current-container-id))
                                      ((om/get-shared owner :track-event) {:event-type :build-page-tab-clicked
                                                                           :properties {:selected-tab-name selected-tab-name}}))})}
         (html
          [:div.card.sub-head-content {:class (str "sub-head-" (name selected-tab-name))}
           (case selected-tab-name

             :tests (om/build build-tests-list {:build-data build-data
                                                :project project})

             :build-timing (om/build build-timings/build-timings {:build (select-keys build [:parallel :steps :start_time :stop_time])
                                                                  :project project
                                                                  :plan plan})

             :artifacts (om/build build-artifacts-list
                                  {:artifacts-data (get build-data :artifacts-data) :user user
                                   :has-artifacts? (:has_artifacts build)})

             :config (om/build build-config {:config-string (get-in build [:circle_yml :string])
                                             :build build
                                             :build-data build-data})

             :build-parameters (om/build build-parameters {:build-parameters build-params})

             :usage-queue (om/build build-queue {:build build
                                                 :builds (:builds usage-queue-data)
                                                 :plan plan})
             :ssh-info (om/build build-ssh {:build build :user user})

             ;; avoid errors if a nonexistent tab is typed in the URL
             nil)]))))))

(defn build-canceler [{:keys [type name handle]}]
  [:span.summary-label
   (list "Canceled by: "
         [:a {:href (case type
                      "github" (str (github-endpoint) "/" handle)
                      "bitbucket" (bb-utils/user-profile-url handle)
                      nil)}
          (if (not-empty name) name handle)])])

(defn pull-requests [{:keys [urls]} owner]
  ;; It's possible for a build to be part of multiple PRs, but it's rare
  [:div.summary-item
   [:span.summary-label
    (str "PR"
         (when (< 1 (count urls)) "s")
         ": ")]
   [:span
    (interpose
     ", "
     (for [url urls]
       [:a {:href url
            :on-click #((om/get-shared owner :track-event) {:event-type :build-head-pr-link-clicked})}
        "#"
        (let [[_ number] (re-find #"/(\d+)$" url)]
          (or number "?"))]))]])

(defn queued-time [build]
  (if (< 0 (build-model/run-queued-time build))
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
     " waiting for builds to finish"]))

(defn build-running-status [{start-time :start_time
                             :as build}]
  {:pre [(some? start-time)]}
  [:div.summary-item
   [:span.summary-label "Started: "]
   [:span.start-time
    {:title (datetime/full-datetime start-time)}
    (om/build common/updating-duration
              {:start start-time})
    " ago"]])

(defn build-finished-status [{stop-time :stop_time
                              :as build}]
  {:pre [(some? stop-time)]}
  [:div.summary-item
   [:span.summary-label "Finished: "]
   [:span.stop-time
    {:title (datetime/full-datetime stop-time)}
    (om/build common/updating-duration
              {:start stop-time}
              {:opts {:formatter datetime/time-ago-abbreviated}})
    " ago"]
   (str " (" (build-model/duration build) ")")])

(defrender previous-build-label [{:keys [previous] vcs-url :vcs_url} owner]
  (when-let [build-number (:build_num previous)]
    (html
     [:div.summary-item
      [:span.summary-label "Previous: "]
      [:a {:href (routes/v1-build-path (vcs-url/vcs-type vcs-url) (vcs-url/org-name vcs-url) (vcs-url/repo-name vcs-url) build-number)}
       build-number]])))

(defn expected-duration
  [build owner opts]
  (reify
    om/IDisplayName
    (display-name [_] "Expected Duration")

    om/IRender
    (render [_]
      (let [formatter (get opts :formatter datetime/as-duration)
            previous-build (:previous_successful_build build)
            past-ms (:build_time_millis previous-build)]
        (html
          [:div.summary-item
           [:span.summary-label "Estimated: "]
           [:span (formatter past-ms)]])))))

(defn build-head [data owner]
  (reify
    om/IRender
    (render [_]
      (let [build-data (:build-data data)
            build (:build build-data)
            build-id (build-model/id build)
            build-num (:build_num build)
            vcs-url (:vcs_url build)
            current-container-id (:current-container-id data)
            usage-queue-data (:usage-queue-data build-data)
            run-queued? (build-model/in-run-queue? build)
            usage-queued? (build-model/in-usage-queue? build)
            project (get-in data [:project-data :project])
            plan (get-in data [:project-data :plan])
            user (:user data)
            logged-in? (not (empty? user))
            config-data (:config-data build-data)
            build-info {:build-id (build-model/id build)
                        :vcs-url (:vcs_url build)
                        :build-num (:build_num build)}]
        (html
          [:div
           [:div.summary-header
            [:div.summary-items
             [:div.summary-item
              (builds-table/build-status-badge build)]
             (if-not (:stop_time build)
               (when (:start_time build)
                 (build-running-status build))
               (build-finished-status build))]
            [:div.summary-items
             (when (build-model/running? build)
               (om/build expected-duration build))
             (om/build previous-build-label build)
             (when (project-model/parallel-available? project)
               [:div.summary-item
                [:span.summary-label "Parallelism: "]
                [:a.parallelism-link-head {:title (str "This build used " (:parallel build) " containers. Click here to change parallelism for future builds.")
                                           :on-click #((om/get-shared owner :track-event) {:event-type :build-head-parallelism-clicked
                                                                                           :properties {:repo (project-model/repo-name project)
                                                                                                        :org (project-model/org-name project)}})
                                           :href (build-model/path-for-parallelism build)}
                 (let [parallelism (str (:parallel build) "x")]
                   (if (enterprise?)
                     parallelism
                     (str parallelism
                          " out of "
                          (min (+ (plan-model/linux-containers plan)
                                  (if (project-model/oss? project)
                                    plan-model/oss-containers
                                    0))
                               (plan-model/max-parallelism plan))
                          "x")))]])]
           (when (:usage_queued_at build)
             [:div.summary-items
              [:div.summary-item
               [:span.summary-label "Queued: "]
               [:span (queued-time build)]]])

           [:div.summary-build-contents
            [:div.summary-item
             [:span.summary-label "Triggered by: "]
             [:span (trigger-html build)]]

            (when-let [urls (seq (:pull_request_urls build))]
              (pull-requests {:urls urls} owner))]]

          (when-let  [canceler  (and  (=  (:status build) "canceled")
                                      (:canceler build))]
            [:div.summary-header
             [:div.summary-items
              [:div.summary-item
               (build-canceler canceler)]]])
          [:div.card
           [:div.small-emphasis
            (let [n (-> build :all_commit_details count)]
              (if (:all_commit_details_truncated build)
                (gstring/format "Last %d Commits" n)
                (gstring/format "Commits (%d)" n)))]
           (om/build build-commits build-data)]
          [:div.build-head-wrapper
           [:div.build-head
            (om/build build-sub-head data)]]])))))
