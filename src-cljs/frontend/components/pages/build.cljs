(ns frontend.components.pages.build
  (:require [frontend.async :refer [raise!]]
            [frontend.experiments.no-test-intervention :as no-test-intervention]
            [frontend.components.build :as build-com]
            [frontend.components.build-head :as build-head]
            [frontend.components.forms :as forms]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.jira-modal :as jira-modal]
            [frontend.components.templates.main :as main-template]
            [frontend.experiments.open-pull-request :refer [open-pull-request-action]]
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

    om/IDidMount
    (did-mount [_]
      (utils/tooltip ".rebuild-container"))

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
            action-for  #(-> actions % :action)
            can-trigger-builds? (project-model/can-trigger-builds? project)]
        (html
         [:div.rebuild-container
          (when-not can-trigger-builds?
            {:data-original-title "You need write permissions to trigger builds."
             :data-placement "left"})
          [:button.rebuild
           {:on-click (action-for :rebuild)
            ;; using :disabled also disables tooltips when hovering over button
            :class (when-not can-trigger-builds? "disabled")}
           [:img.rebuild-icon {:src (utils/cdn-path (str "/img/inner/icons/Rebuild.svg"))}]
           rebuild-status]
          [:span.dropdown.rebuild
           [:i.fa.fa-chevron-down.dropdown-toggle {:data-toggle "dropdown"}]
           [:ul.dropdown-menu.pull-right
            [:li {:class (when-not can-trigger-builds? "disabled")}
             [:a {:on-click (action-for :without_cache)} (text-for :without_cache)]]
            [:li {:class (when-not (and can-trigger-builds? (ssh-available? project build)) "disabled")}
             [:a {:on-click (action-for :with_ssh)} (text-for :with_ssh)]]]]])))))

(defn- header-actions
  [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:show-jira-modal? false
       :show-setup-docs-modal? false})

    om/IDidMount
    (did-mount [_]
      (utils/tooltip ".build-settings"))

    om/IWillReceiveProps
    (will-receive-props [_ data]
      (let [build (get-in data state/build-path)
            show-setup-docs-modal? (no-test-intervention/show-setup-docs-modal? build)]
        (om/set-state! owner :show-setup-docs-modal? show-setup-docs-modal?)))

    om/IRenderState
    (render-state [_ {:keys [show-jira-modal? show-setup-docs-modal?]}]
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
            can-write-settings? (project-model/can-write-settings? project)
            track-event (fn [event-type]
                          ((om/get-shared owner :track-event)
                           {:event-type event-type
                            :properties {:project-vcs-url (:vcs_url project)
                                         :user (:login user)
                                         :component "header"}}))]
        (html
          [:div.build-actions-v2
           ;; Ensure we never have more than 1 modal showing
           (cond
             show-jira-modal?
             (om/build jira-modal/jira-modal {:project project
                                              :jira-data jira-data
                                              :close-fn #(om/set-state! owner :show-jira-modal? false)})
             (and show-setup-docs-modal?
                  (= :setup-docs-modal (no-test-intervention/ab-test-treatment)))
             (om/build no-test-intervention/setup-docs-modal
                       {:close-fn
                        #(om/set-state! owner :show-setup-docs-modal? false)}))
           ;; Cancel button
           (when (and (build-model/can-cancel? build) can-trigger-builds?)
             (button/managed-button
               {:loading-text "Canceling"
                :failed-text  "Couldn't Cancel"
                :success-text "Canceled"
                :kind :primary
                :on-click #(raise! owner [:cancel-build-clicked (build-model/build-args build)])}
               "Cancel Build"))
           ;; Rebuild button
           (om/build rebuild-actions {:build build :project project})
           ;; PR button
           (when (and (feature/enabled? :open-pull-request)
                      (not-empty build))
             [:div.with-border
              (om/build open-pull-request-action {:build build})])
           ;; JIRA button
           (when (and (feature/enabled? :jira-integration) jira-data can-write-settings?)
             [:div.with-border
              (button/icon {:label "Add ticket to JIRA"
                            :on-click #(om/set-state! owner :show-jira-modal? true)}
                           (icon/add-jira-issue))])
           ;; Settings button
           [:div.build-settings.with-border
            (when-not can-write-settings?
              {:class "disabled"
               :data-original-title "You need to be an admin to change project settings."
               :data-placement "left"})
            (button/icon-link {:href (routes/v1-project-settings-path (:navigation-data data))
                               :label "Project Settings"
                               :on-click #((om/get-shared owner :track-event)
                                           {:event-type :project-settings-clicked
                                            :properties {:project-vcs-url (:vcs_url project)
                                                         :user (:login user)}})}
                              (icon/settings))]])))))

(defn page [app owner]
  (reify
    om/IRender
    (render [_]
      (main-template/template
       {:app app
        :main-content (om/build build-com/build
                                {:app app
                                 :ssh-available? (ssh-available? (get-in app (get-in app state/project-path))
                                                                 (get-in app (get-in app state/build-path)))})
        :header-actions (om/build header-actions app)}))))
