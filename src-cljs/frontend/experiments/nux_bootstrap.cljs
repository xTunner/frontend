(ns frontend.experiments.nux-bootstrap
  (:require [frontend.async :refer [raise!]]
            [frontend.api :as api]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.models.project :as project-model]
            [frontend.state :as state]
            [frontend.utils :as utils]
            [frontend.utils.github :as gh-utils]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn nux-bootstrap-content [data owner]
  (let [building-projects (:building-projects data)
        not-building-projects (:not-building-projects data)
        cta-button-text (if (not-empty not-building-projects) "Follow and Build" "Follow")
        event-properties (let [selected-building-projects-count (->> building-projects
                                                                     (filter :checked)
                                                                     count)
                               selected-not-building-projects-count (->> not-building-projects
                                                                         (filter :checked)
                                                                         count)]
                           {:button-text cta-button-text
                            :selected-building-projects-count selected-building-projects-count
                            :selected-not-building-projects-count selected-not-building-projects-count
                            :displayed-building-projects-count (count building-projects)
                            :displayed-not-building-projects-count (count not-building-projects)
                            :total-displayed-projects-count (+ (count building-projects)
                                                               (count not-building-projects))
                            :total-selected-projects-count (+ selected-building-projects-count
                                                              selected-not-building-projects-count)})]
    (reify
      om/IDidMount
      (did-mount [_]
        ((om/get-shared owner :track-event) {:event-type :nux-bootstrap-impression
                                             :properties event-properties}))
      om/IRender
      (render [_]
        (html
          (let [project-checkboxes (fn [projects path]
                                     [:div.projects
                                      {:class (str (when (< 7 (count projects)) "eight-projects")
                                                   " "
                                                   (when (< 11 (count projects)) "twelve-projects"))}
                                      (->> projects
                                          (map
                                            (fn [project]
                                              [:div.checkbox
                                               [:label
                                                [:input {:type "checkbox"
                                                         :checked (:checked project)
                                                         :name "follow checkbox"
                                                         :on-click #(utils/toggle-input owner (conj path
                                                                                                    (:vcs_url project)
                                                                                                    :checked)
                                                                                        %)}]
                                                (project-model/project-name project)]])))])
                deselect-activity-repos (fn [path]
                                          ((om/get-shared owner :track-event) {:event-type :deselect-all-projects-clicked
                                                                               :properties event-properties})
                                          (raise! owner [:deselect-activity-repos {:path path}]))]
            (card/titled
              {:title "Getting Started"}
              (html
                (when (or (not-empty building-projects)
                          (not-empty not-building-projects))
                  [:div.getting-started
                   (when (not-empty building-projects)
                     [:div
                      [:h2.no-top-padding "Follow projects"]
                      [:div "These projects are already building on CircleCI. Would you like to follow them?"]
                      (project-checkboxes building-projects (state/vcs-recent-active-projects-path true :github))
                      [:a {:on-click #(deselect-activity-repos (state/vcs-recent-active-projects-path true :github))}
                       "Deselect all projects"]
                      (when (not-empty not-building-projects)
                        [:hr])])
                   (when (not-empty not-building-projects)
                     [:div
                      [:h2 "Build projects"]
                      [:div "These are your projects that are not building on CircleCI yet. Would you like to start building and following these?"]
                      (project-checkboxes not-building-projects (state/vcs-recent-active-projects-path false :github))
                      [:a {:on-click #(deselect-activity-repos (state/vcs-recent-active-projects-path false :github))}
                       "Deselect all projects"]])

                   (button/managed-button {:kind :primary
                                           :loading-text "Following..."
                                           :failed-text "Failed"
                                           :success-text "Success!"
                                           :disabled? (->> (concat building-projects not-building-projects)
                                                           (some :checked)
                                                           not)
                                           :on-click #(do
                                                        ((om/get-shared owner :track-event) {:event-type :follow-and-build-projects-clicked
                                                                                             :properties event-properties})
                                                        (raise! owner [:followed-projects]))}
                                          cta-button-text)])))))))))

(defn build-empty-state [{:keys [projects-loaded?] :as data} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (api/get-vcs-activity (om/get-shared owner [:comms :api])))
    om/IRender
    (render [_]
      (let [avatar-url (get-in data [:current-user :identities :github :avatar_url])]
        (html
          [:div.no-projects-block
           (card/collection
             [(card/basic
                (empty-state/empty-state
                  {:icon (empty-state/avatar-icons
                           [(gh-utils/make-avatar-url {:avatar_url avatar-url} :size 60)])
                   :heading (html [:span (empty-state/important "Welcome to CircleCI!")])
                   :subheading (html
                                 [:div
                                  [:div "Build and follow projects to populate your dashboard and receive build status emails."]
                                  [:div "To get started, here are the projects that you’ve committed to recently."]])}))

              (if projects-loaded?
                (om/build nux-bootstrap-content data)
                (card/basic (spinner)))
              (if projects-loaded?
                (card/titled
                  {:title "Looking for something else?"}
                  (html
                    [:div
                     [:div
                      "Project not listed? Visit the "
                      [:a {:href "/add-projects"} "Add Projects"]
                      " page to find it."]
                     [:div
                      "Interested in a demo? "
                      [:a {:href "/gh/facebook/fresco"
                           :on-click #((om/get-shared owner :track-event) {:event-type :view-demo-clicked})}
                       "See how Facebook uses CircleCI"]]])))])])))))
