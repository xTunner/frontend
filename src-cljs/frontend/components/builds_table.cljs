(ns frontend.components.builds-table
  (:require [cemerick.url :as url]
            [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.analytics :as analytics]
            [frontend.async :refer [raise!]]
            [frontend.datetime :as datetime]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.svg :refer [svg]]
            [frontend.models.build :as build-model]
            [frontend.models.feature :as feature]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
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

(defn build-row [build owner {:keys [show-actions? show-branch? show-project?]}]
  (let [url (build-model/path-for (select-keys build [:vcs_url]) build)]
    [:div.build {:class (cond-> [(build-model/status-class build)]
                          (:dont_build build) (conj "dont_build"))}
     [:div.status-area
      [:a {:href url
           :title (build-model/status-words build)}
       (build-status-badge build)]

      (when (build-model/can-cancel? build)
        [:div.build-actions
         (let [build-id (build-model/id build)
               vcs-url (:vcs_url build)
               build-num (:build_num build)]
           (forms/managed-button
            [:button.cancel-build
             {:data-loading-text "Canceling..."
              :on-click #(raise! owner [:cancel-build-clicked {:build-id build-id
                                                               :vcs-url vcs-url
                                                               :build-num build-num}])}
             [:img.cancel-icon {:src (common/icon-path "Status-Canceled")}]
             [:span.cancel-text "cancel"]]))])]

     [:div.build-info
      [:div.build-info-header
       [:div.contextual-identifier
        [:a {:title (str (:username build) "/" (:reponame build) " #" (:build_num build))
             :href url}

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
        (when-let [urls (seq (:pull_request_urls build))]
          [:div.metadata-item.pull-requests {:title "Pull Requests"}
           [:i.octicon.octicon-git-pull-request]
           (interpose
            ", "
            (for [url urls]
              [:a {:href url
                   :on-click #(analytics/track {:event-type :pr-link-clicked
                                                :owner owner
                                                :properties {:repo (:reponame build)
                                                             :org (:username build)}})}
               "#"
               (let [[_ number] (re-find #"/(\d+)$" url)]
                 (or number "?"))]))])

        [:div.metadata-item.revision
         [:i.octicon.octicon-git-commit]
         (when (:vcs_revision build)
           [:a {:title (build-model/github-revision build)
                :href (build-model/github-commit-url build)
                :on-click #(analytics/track {:event-type :revision-link-clicked
                                             :owner owner
                                             :properties {:repo (:reponame build)
                                                          :org (:username build)}})}
            (build-model/github-revision build)])]]]]))

(defn builds-table [builds owner {:keys [show-actions? show-branch? show-project?]
                                  :or {show-branch? true
                                       show-project? true}}]
  (reify
    om/IDisplayName (display-name [_] "Builds Table V2")
    om/IRender
    (render [_]
      (html
        [:div.container-fluid
         (map #(build-row % owner {:show-actions? show-actions?
                                   :show-branch? show-branch?
                                   :show-project? show-project?})
              builds)]))))

