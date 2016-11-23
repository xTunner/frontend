(ns frontend.components.pages.build
  (:require [frontend.async :refer [raise!]]
            [frontend.components.build :as build-com]
            [frontend.components.build-head :as build-head]
            [frontend.components.forms :as forms]
            [frontend.components.jira-modal :as jira-modal]
            [frontend.components.templates.main :as main-template]
            [frontend.models.build :as build-model]
            [frontend.models.feature :as feature]
            [frontend.models.project :as project-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.stefon :refer [asset-path]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn- ssh-available?
  "Show the SSH button unless it's disabled"
  [project build]
  (not (or (project-model/feature-enabled? project :disable-ssh)
           (:ssh_disabled build))))

(defn- rebuild-actions [{:keys [build project]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:rebuild-status "Rebuild"})

    om/IWillReceiveProps
    (will-receive-props [_ {:keys [build]}]
      (when (build-model/running? build)
        (om/set-state! owner [:rebuild-status] "Rebuild")))

    om/IRenderState
    (render-state [_ {:keys [rebuild-status]}]
      (let [rebuild-args (merge (build-model/build-args build) {:component "rebuild-dropdown"})
            update-status!  #(om/set-state! owner [:rebuild-status] %)
            actions         {:rebuild
                             {:text  "Rebuild"
                              :title "Retry the same tests"
                              :action #(do (raise! owner [:retry-build-clicked (merge rebuild-args {:no-cache? false})])
                                           (update-status! "Rebuilding..."))}

                             :without_cache
                             {:text  "Rebuild without cache"
                              :title "Retry without cache"
                              :action #(do (raise! owner [:retry-build-clicked (merge rebuild-args {:no-cache? true})])
                                           (update-status! "Rebuilding..."))}

                             :with_ssh
                             {:text  "Rebuild with SSH"
                              :title "Retry with SSH in VM",
                              :action #(do (raise! owner [:ssh-build-clicked rebuild-args])
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
            (when (ssh-available? project build)
              [:li
               [:a {:on-click (action-for :with_ssh)} (text-for :with_ssh)]])]]])))))

(defn- header-actions
  [data owner]
  (reify
    om/IInitState
    (init-state [_]
      :show-modal? false)
    om/IRenderState
    (render-state [_ {:keys [show-modal?]}]
      (let [build-data (dissoc (get-in data state/build-data-path) :container-data)
            build (get-in data state/build-path)
            build-id (build-model/id build)
            build-num (:build_num build)
            vcs-url (:vcs_url build)
            project (get-in data state/project-path)
            user (get-in data state/user-path)
            logged-in? (not (empty? user))
            jira-data (get-in data state/jira-data-path)
            can-trigger-builds? (project-model/can-trigger-builds? project)
            can-write-settings? (project-model/can-write-settings? project)]
        (html
          [:div.build-actions-v2
           [:div
            (when show-modal?
              (om/build jira-modal/jira-modal {:project project
                                               :jira-data jira-data
                                               :close-fn #(om/set-state! owner :show-modal? false)}))]
           (when (and (build-model/can-cancel? build) can-trigger-builds?)
             (forms/managed-button
               [:a.cancel-build
                {:data-loading-text "canceling"
                 :title             "cancel this build"
                 :on-click #(raise! owner [:cancel-build-clicked (build-model/build-args build)])}
                "cancel build"]))
           (when can-trigger-builds?
             (om/build rebuild-actions {:build build :project project}))
           (when can-write-settings?
             (if (feature/enabled? :jira-integration)
               (list
                 [:button.btn-icon.jira-container
                   {:on-click #(om/set-state! owner :show-modal? true)
                    :title "Add ticket to JIRA"}
                   [:img.add-jira-ticket-icon {:src (utils/cdn-path (str "/img/inner/icons/create-jira-issue.svg"))}]]
                 [:a.exception.btn-icon.build-settings-container
                  {:href (routes/v1-project-settings-path (:navigation-data data))
                   :on-click #((om/get-shared owner :track-event) {:event-type :project-settings-clicked
                                                                   :properties {:project (:vcs_url project)
                                                                                :user (:login user)}})
                    :title "Project settings"}
                  [:i.material-icons "settings"]])
               [:div.build-settings
                [:a.build-action
                 {:href (routes/v1-project-settings-path (:navigation-data data))
                  :on-click #((om/get-shared owner :track-event) {:event-type :project-settings-clicked
                                                                  :properties {:project (:vcs_url project)
                                                                               :user (:login user)}})}
                 [:i.material-icons "settings"]
                 "Project Settings"]]))])))))

(defn page [app owner]
  (reify
    om/IRender
    (render [_]
      (om/build main-template/template
                {:app app
                 :main-content (om/build build-com/build
                                         {:app app
                                          :ssh-available? (ssh-available? (get-in app (get-in app state/project-path))
                                                                          (get-in app (get-in app state/build-path)))})
                 :header-actions (om/build header-actions app)}))))
