(ns frontend.components.pages.build.head
  (:require [cljs.core.async :as async :refer [chan]]
            [clojure.string :as string]
            [devcards.core :as dc :refer-macros [defcard-om]]
            [frontend.async :refer [raise!]]
            [frontend.components.build-head :as old-build-head]
            [frontend.components.common :as common]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.status :as status]
            [frontend.config :refer [enterprise? github-endpoint]]
            [frontend.datetime :as datetime]
            [frontend.models.build :as build-model]
            [frontend.models.plan :as plan-model]
            [frontend.models.project :as project-model]
            [frontend.routes :as routes]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.bitbucket :as bb-utils]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component defrender element html]]))

(defn- summary-item [label value]
  (component
    (html
     [:.summary-item
      (when label
        [:span.summary-label label])
      value])))

(defn- linkify [text]
  (let [url-pattern #"(?im)(\b(https?|ftp)://[-A-Za-z0-9+@#/%?=~_|!:,.;]*[-A-Za-z0-9+@#/%=~_|])"
        pseudo-url-pattern #"(?im)(^|[^/])(www\.[\S]+(\b|$))"]
    (-> text
        ;; TODO: switch to clojure.string/replace when they fix
        ;; http://dev.clojure.org/jira/browse/CLJS-485...
        (.replace (js/RegExp. (.-source url-pattern) "gim")
                  "<a href=\"$1\" target=\"_blank\">$1</a>")
        (.replace (js/RegExp. (.-source pseudo-url-pattern) "gim")
                  "$1<a href=\"http://$2\" target=\"_blank\">$2</a>"))))

(defn- maybe-project-linkify [text vcs-type project-name]
  (if-not project-name
    text
    (let [issue-pattern #"(^|\s)#(\d+)\b"]
      (cond
        (and (= vcs-type "bitbucket") (re-find #"pull request #\d+" text))
        (string/replace
          text
          issue-pattern
          (gstring/format "$1<a href='%s/%s/pull-requests/$2' target='_blank'>pull request #$2</a>"
                          (bb-utils/http-endpoint)
                          project-name))
        :else
        (string/replace
          text
          issue-pattern
          (gstring/format "$1<a href='%s/%s/issues/$2' target='_blank'>#$2</a>"
                          (case vcs-type
                            "github" (gh-utils/http-endpoint)
                            "bitbucket" (bb-utils/http-endpoint))
                          project-name))))))

(defn- commit-line [{:keys [author_name build subject body commit_url commit] :as commit-details} owner]
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
                                   :on-click #((om/get-shared owner :track-event) {:event-type :revision-link-clicked})}
         (subs commit 0 7)]
        [:span.commit-message
         {:title body
          :id (str "commit-line-tooltip-hack-" commit)
          :dangerouslySetInnerHTML {:__html (let [vcs-url (:vcs_url build)]
                                              (-> subject
                                                  (gstring/htmlEscape)
                                                  (linkify)
                                                  (maybe-project-linkify (vcs-url/vcs-type vcs-url)
                                                                         (vcs-url/project-name vcs-url))))}}]]))))

(def ^:private initial-build-commits-count 3)

(defn- build-commits [{:keys [build show-all-commits?]} owner]
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
                 (om/build-all commit-line top-commits {:key :commit})
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
                   (om/build-all commit-line bottom-commits {:key :commit}))))])])))))

(defn- pull-requests [{:keys [urls]} owner]
  ;; It's possible for a build to be part of multiple PRs, but it's rare
  (summary-item
   (str "PR"
        (when (< 1 (count urls)) "s")
        ": ")
   [:span
    (interpose
     ", "
     (for [url urls]
       ;; WORKAROUND: We have/had a bug where a PR URL would be reported as nil.
       ;; When that happens, this code blows up the page. To work around that,
       ;; we just skip the PR if its URL is nil.
       (when url
         [:a {:href url
              :on-click #((om/get-shared owner :track-event) {:event-type :pr-link-clicked})}
          "#"
          (gh-utils/pull-request-number url)])))]))

(defn- build-canceler [{:keys [type name login]}]
  [:span
   (list
         [:a {:href (case type
                      "github" (str (github-endpoint) "/" login)
                      "bitbucket" (bb-utils/user-profile-url login)
                      nil)}
          (if (not-empty name) name login)])])

(defn- link-to-retry-source [build]
  (when-let [retry-id (:retry_of build)]
    [:a {:href (routes/v1-build-path
                 (vcs-url/vcs-type (:vcs_url build))
                 (:username build)
                 (:reponame build)
                 nil
                 retry-id)}
     retry-id]))

(defn- link-to-user [build]
  (when-let [user (:user build)]
    (if (= "none" (:login user))
      [:em "Unknown"]
      [:a {:href (vcs-url/profile-url user)}
       (build-model/ui-user build)])))

(defn- link-to-commit [build]
  [:a {:href (:compare build)}
   (take 7 (:vcs_revision build))])

(defn- trigger-html [build]
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

(defn- queued-time [build]
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

(defrender previous-build-label [{:keys [previous] vcs-url :vcs_url} owner]
  (when-let [build-number (:build_num previous)]
    (html
     (summary-item
      "Previous: "
      [:a {:href (routes/v1-build-path (vcs-url/vcs-type vcs-url) (vcs-url/org-name vcs-url) (vcs-url/repo-name vcs-url) nil build-number)}
       build-number]))))

(defn- expected-duration
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
         (summary-item
          "Estimated: "
          [:span (formatter past-ms)]))))))

(defn- build-running-status [{start-time :start_time
                             :as build}]
  {:pre [(some? start-time)]}
  (summary-item
   "Started: "
   [:span.start-time
    {:title (datetime/full-datetime start-time)}
    (om/build common/updating-duration
              {:start start-time})
    " ago"]))

(defn- build-finished-status [{stop-time :stop_time
                              :as build}]
  {:pre [(some? stop-time)]}
  (summary-item
   "Finished: "
   (list
    [:span.stop-time
     {:title (datetime/full-datetime stop-time)}
     (om/build common/updating-duration
               {:start stop-time}
               {:opts {:formatter datetime/time-ago-abbreviated}})
     " ago"]
    (str " (" (build-model/duration build) ")"))))

(defn- build-head-content [{:keys [build-data project-data] :as data} owner]
  (reify
    om/IRender
    (render [_]
      (component
        (let [{:keys [stop_time start_time parallel usage_queued_at
                      pull_requests status canceler
                      all_commit_details all_commit_details_truncated] :as build} (:build build-data)
              {:keys [project plan]} project-data]
          (html
           [:div
            [:.summary-header
             (summary-item nil (status/build-badge (build-model/build-status build)))
             (if-not stop_time
               (when start_time
                 (build-running-status build))
               (build-finished-status build))
             (when (build-model/running? build)
               (om/build expected-duration build))
             (om/build previous-build-label build)
             (when (project-model/parallel-available? project)
               (summary-item
                "Parallelism: "
                [:a.parallelism-link-head {:title (str "This build used " parallel " containers. Click here to change parallelism for future builds.")
                                           :on-click #((om/get-shared owner :track-event) {:event-type :parallelism-clicked
                                                                                           :properties {:repo (project-model/repo-name project)
                                                                                                        :org (project-model/org-name project)}})
                                           :href (build-model/path-for-parallelism build)}
                 (let [parallelism (str parallel "x")]
                   (if (enterprise?)
                     parallelism
                     (str parallelism
                          " out of "
                          (min (+ (plan-model/linux-containers plan)
                                  (if (project-model/oss? project)
                                    plan-model/oss-containers
                                    0))
                               (plan-model/max-parallelism plan))
                          "x")))]))
             (when usage_queued_at
               (summary-item
                "Queued: "
                [:span (queued-time build)]))

             [:.right-side
              (summary-item
               "Triggered by: "
               [:span (trigger-html build)])

              (when-let [canceler (and (= status "canceled")
                                       canceler)]
                (summary-item
                 "Canceled by: "
                 [:span (build-canceler canceler)]))

              (when (build-model/has-pull-requests? build)
                (pull-requests {:urls (map :url pull_requests)} owner))]]

            (card/basic
             (element :commits
               (html
                [:div
                 [:.heading
                  (let [n (count all_commit_details)]
                    (if all_commit_details_truncated
                      (gstring/format "Last %d Commits" n)
                      (gstring/format "Commits (%d)" n)))]
                 (om/build build-commits build-data)])))]))))))

(defn build-head [{:keys [build-data project-data workflow-data] :as data} owner]
  (reify
    om/IRender
    (render [_]
      (component
        (html
         [:div
          (if workflow-data
            (om/build old-build-head/build-head-content-workflow (select-keys data [:build-data :project-data :workflow-data]))
            (om/build build-head-content (select-keys data [:build-data :project-data])))
          [:div.build-sub-head
           (om/build old-build-head/build-sub-head data)]])))))

(dc/do
  (defcard-om build-head
    build-head
    {:build-data {:build {:vcs_url "https://github.com/acme/anvil"
                          :status "success"}}}
    {:shared {:comms {:controls (chan)}
              :track-event (constantly nil)}}))
