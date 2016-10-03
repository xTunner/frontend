(ns frontend.components.pages.build
  (:require [frontend.async :refer [raise!]]
            [frontend.components.build :as build-com]
            [frontend.components.forms :as forms]
            [frontend.components.templates.main :as main-template]
            [frontend.models.build :as build-model]
            [frontend.models.feature :as feature]
            [frontend.models.project :as project-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn- ssh-available?
  "Show the SSH button unless it's disabled"
  [project]
  (not (project-model/feature-enabled? project :disable-ssh)))

(defn- rebuild-actions [{:keys [build project]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:rebuild-status "Rebuild"})

    om/IWillUpdate
    (will-update [_ {:keys [build]} _]
      (when (build-model/running? build)
        (om/set-state! owner [:rebuild-status] "Rebuild")))

    om/IRenderState
    (render-state [_ {:keys [rebuild-status]}]
      (let [rebuild-args (build-model/build-args build)
            update-status!  #(om/set-state! owner [:rebuild-status] %)
            rebuild!        #(raise! owner %)
            actions         {:rebuild
                             {:text  "Rebuild"
                              :title "Retry the same tests"
                              :action #(do (rebuild! [:retry-build-clicked (merge rebuild-args {:no-cache? false})])
                                           (update-status! "Rebuilding..."))}

                             :without_cache
                             {:text  "Rebuild without cache"
                              :title "Retry without cache"
                              :action #(do (rebuild! [:retry-build-clicked (merge rebuild-args {:no-cache? true})])
                                           (update-status! "Rebuilding..."))}

                             :with_ssh
                             {:text  "Rebuild with SSH"
                              :title "Retry with SSH in VM",
                              :action #(do (rebuild! [:ssh-build-clicked rebuild-args])
                                           (update-status! "Rebuilding..."))}}
            text-for    #(-> actions % :text)
            action-for  #(-> actions % :action)]
        (html
         [:div.rebuild-container
          [:button.rebuild {:on-click (action-for :rebuild)}
           [:img.rebuild-icon {:src (utils/cdn-path (str "/img/inner/icons/Rebuild.svg"))}]
           rebuild-status]
          [:span.dropdown.rebuild
           [:i.fa.fa-chevron-down.dropdown-toggle {:data-toggle "dropdown"}]
           [:ul.dropdown-menu.pull-right
            [:li
             [:a {:on-click (action-for :without_cache)} (text-for :without_cache)]]
            (when (ssh-available? project)
              [:li
               [:a {:on-click (action-for :with_ssh)} (text-for :with_ssh)]])]]])))))

(defn- merge-actions [{:keys [build]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      ((om/get-shared owner :track-event) {:event-type :merge-pr-impression}))
    om/IRender
    (render [_]
      (html
       [:div.merge-container
        [:button {:on-click #(do ((om/get-shared owner :track-event) {:event-type :merge-pr-clicked})
                                 (raise! owner [:merge-pull-request-clicked (build-model/merge-args build)]))
                  :data-toggle "tooltip"
                  :data-placement "bottom"
                  :title (str "Merge PR #" (last (build-model/pull-request-numbers build)))}
         [:i.octicon.octicon-git-merge.merge-icon]
         "Merge PR"]]))))

(defn- header-actions
  [data owner]
  (reify
    om/IRender
    (render [_]
      (let [build (get-in data state/build-path)
            project (get-in data state/project-path)
            user (get-in data state/user-path)
            can-trigger-builds? (project-model/can-trigger-builds? project)
            can-write-settings? (project-model/can-write-settings? project)]
        (html
          [:div.build-actions-v2
           (when (and (build-model/can-cancel? build) can-trigger-builds?)
             (forms/managed-button
               [:a.cancel-build
                {:data-loading-text "canceling"
                 :title             "cancel this build"
                 :on-click #(raise! owner [:cancel-build-clicked (build-model/build-args build)])}
                "cancel build"]))
           (when can-trigger-builds?
             (om/build rebuild-actions {:build build :project project}))
           (when (and (feature/enabled? :merge-pull-request)
                      can-write-settings?
                      (build-model/can-merge-at-least-one-pr? build))
             (om/build merge-actions {:build build}))
           (when can-write-settings?
             [:div.build-settings
              [:a.build-action
               {:href (routes/v1-project-settings-path (:navigation-data data))
                :on-click #((om/get-shared owner :track-event) {:event-type :project-settings-clicked
                                                                :properties {:project (:vcs_url project)
                                                                             :user (:login user)}})}
               [:i.material-icons "settings"]
               "Project Settings"]])])))))

(defn page [app owner]
  (reify
    om/IRender
    (render [_]
      (om/build main-template/template
                {:app app
                 :main-content (om/build build-com/build
                                         {:app app
                                          :ssh-available? (ssh-available? (get-in app (get-in app state/project-path)))})
                 :header-actions (om/build header-actions app)}))))
