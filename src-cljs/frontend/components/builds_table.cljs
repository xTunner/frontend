(ns frontend.components.builds-table
  (:require [cemerick.url :as url]
            [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.datetime :as datetime]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.svg :refer [svg]]
            [frontend.models.build :as build-model]
            [frontend.models.project :as project-model]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontend.utils.github :as gh-utils])
  (:require-macros [frontend.utils :refer [html]]))

(defn dashboard-icon [name]
  [:img.dashboard-icon {:src (utils/cdn-path (str "/img/inner/icons/" name ".svg"))}])

(defn build-status-badge-wording [build]
  (let [wording       (build-model/status-words build)
        too-long?     (> (count wording) 10)]
    [:div {:class (if too-long?
                    "badge-text small-text"
                    "badge-text")}
     wording]))

(defn build-status-badge [build]
  [:div.recent-status-badge {:class (build-model/status-class build)}
   (om/build svg {:class "badge-icon"
                  :src (-> build build-model/status-icon common/icon-path)})
   (build-status-badge-wording build)])

(defn avatar [user & {:keys [size trigger] :or {size 40} :as opts}]
  (if-let [avatar-url (-> user :avatar_url)]
    [:img.dashboard-icon
     ;; Adding `&s=N` to the avatar URL returns an NxN version of the
     ;; avatar (except, for some reason, for default avatars, which are
     ;; always returned full-size, but they're sized with CSS anyhow).
     {:src (-> avatar-url url/url (assoc-in [:query "s"] size) str)}]
    (if (= trigger "api")
      (dashboard-icon "Bot-Icon")
      (dashboard-icon "Default-Avatar"))))

(defn build-action [{:keys [text loading-text icon-name icon-class on-click]}]
  [:div.build-action
   (forms/managed-button
     [:button
      {:data-loading-text loading-text
       :on-click on-click}
      (cond
        icon-name [:img.button-icon {:src (common/icon-path icon-name)}]
        icon-class [:i.button-icon {:class icon-class}])
      [:span.button-text text]])])

(defn build-row [{:keys [build project]} owner {:keys [show-actions? show-branch? show-project?]}]
  (let [url (build-model/path-for (select-keys build [:vcs_url]) build)
        rebuild! #(raise! owner %)
        build-args (merge (build-model/build-args build) {:component "build-row" :is-no-cache false})
        status-words (build-model/status-words build)
        should-show-cancel? (and (project-model/can-trigger-builds? project)
                                 (build-model/can-cancel? build))
        should-show-rebuild? (and (project-model/can-trigger-builds? project)
                                  (#{"timedout" "failed"} (:outcome build)))]
    [:div.build {:class (cond-> [(build-model/status-class build)]
                          (:dont_build build) (conj "dont_build"))}
     [:div.status-area
      [:a {:href url
           :title status-words
           :on-click #((om/get-shared owner :track-event) {:event-type :build-status-clicked
                                                           :properties {:status-words status-words}})}
       (build-status-badge build)]

      ;; Actions should be mutually exclusive. Just in case they
      ;; aren't, use a cond so it doesn't try to render both in the
      ;; same place
      (cond
        should-show-cancel?
        (build-action
          {:text "cancel"
           :loading-text "Cancelling..."
           :icon-name "Status-Canceled"
           :on-click #(do
                        (rebuild! [:cancel-build-clicked build-args])
                        ((om/get-shared owner :track-event) {:event-type :cancel-build-clicked}))})

        should-show-rebuild?
        (build-action
          {:text "rebuild"
           :loading-text "Rebuilding..."
           :icon-name "Rebuild"
           :on-click #(rebuild! [:retry-build-clicked build-args])})
        :else nil)]
     
     [:div.build-info
      [:div.build-info-header
       [:div.contextual-identifier
        [:a {:title (str (:username build) "/" (:reponame build) " #" (:build_num build))
             :href url
             :on-click #((om/get-shared owner :track-event) {:event-type :build-link-clicked
                                                             :properties {:org (vcs-url/org-name (:vcs_url build))
                                                                          :repo (:reponame build)
                                                                          :vcs-type (vcs-url/vcs-type (:vcs_url build))}})}

         (when show-project?
           (str (:username build) " / " (:reponame build) " "))

         (when (and show-project? show-branch?) " / ")

         (when show-branch?
           (-> build build-model/vcs-ref-name))
         " #"
         (:build_num build)]]]
      [:div.recent-commit-msg
       (let [pusher-name (build-model/ui-user build)
             trigger (:why build)]
         [:div.recent-user
          {:title (if (= "api" trigger)
                    "API"
                    pusher-name)
           :data-toggle "tooltip"
           :data-placement "right"}
          (avatar (:user build) :trigger trigger)])
       [:span.recent-log
        {:title (:body build)}
        (:subject build)]]]

     [:div.metadata
      [:div.metadata-row.timing
        (if (or (not (:start_time build))
                (= "not_run" (:status build)))
          (list
           [:div.metadata-item.recent-time.start-time
            {:title "Started: not started"}
            [:i.material-icons "today"]
            "–"]
           [:div.metadata-item.recent-time.duration
            {:title "Duration: not run"}
            [:i.material-icons "timer"]
            "–"])
          (list [:div.metadata-item.recent-time.start-time
                 {:title (str "Started: " (datetime/full-datetime (js/Date.parse (:start_time build))))}
                 [:i.material-icons "today"]
                 (om/build common/updating-duration {:start (:start_time build)} {:opts {:formatter datetime/time-ago-abbreviated}})
                 " ago"]
                [:div.metadata-item.recent-time.duration
                 {:title (str "Duration: " (build-model/duration build))}
                 [:i.material-icons "timer"]
                 (om/build common/updating-duration {:start (:start_time build)
                                                     :stop (:stop_time build)})]))]
      [:div.metadata-row.pull-revision
       (when-let [urls (seq (map :url (:pull_requests build)))]
         [:div.metadata-item.pull-requests {:title "Pull Requests"}
          [:i.octicon.octicon-git-pull-request]
          (interpose
           ", "
           (for [url urls]
             ;; WORKAROUND: We have/had a bug where a PR URL would be reported as nil.
             ;; When that happens, this code blows up the page. To work around that,
             ;; we just skip the PR if its URL is nil.
             (when url
               [:a {:href url
                    :on-click #((om/get-shared owner :track-event) {:event-type :pr-link-clicked
                                                                    :properties {:repo (:reponame build)
                                                                                 :org (:username build)}})}
                "#"
                (gh-utils/pull-request-number url)])))]

        [:div.metadata-item.revision
         [:i.octicon.octicon-git-commit]
         (when (:vcs_revision build)
           [:a {:title (build-model/github-revision build)
                :href (build-model/commit-url build)
                :on-click #((om/get-shared owner :track-event) {:event-type :revision-link-clicked
                                                                :properties {:repo (:reponame build)
                                                                             :org (:username build)}})}
            (build-model/github-revision build)])])]]]))

(defn builds-table [data owner {:keys [show-actions? show-branch? show-project?]
                                :or {show-branch? true
                                     show-project? true}}]
  (let [{:keys [builds projects]} data
        projects-by-vcs_url (into {}
                                  (map (juxt :vcs_url identity) projects))]
    (reify
      om/IDisplayName (display-name [_] "Builds Table V2")
      om/IRender
      (render [_]
        (html
         [:div.container-fluid
          (map #(build-row {:build %
                            :project (get projects-by-vcs_url (:vcs_url %))}
                           owner {:show-actions? show-actions?
                                  :show-branch? show-branch?
                                  :show-project? show-project?})
               builds)])))))
