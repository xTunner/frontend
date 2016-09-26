(ns frontend.components.jira-modal
  (:require [frontend.components.pieces.form :as form]
            [frontend.components.pieces.dropdown :as dropdown]
            [frontend.components.pieces.modal :as modal]
            [frontend.components.pieces.button :as button]
            [frontend.components.forms :as forms]
            [frontend.models.project :as project-model]
            [om.core :as om :include-macros true]
            [frontend.async :refer [navigate! raise!]]
            [frontend.utils.vcs-url :as vcs-url])
  (:require-macros [frontend.utils :refer [defrender html]]))

(defn jira-modal [{:keys [project jira-data close-fn]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:jira-projects (:projects jira-data)
       :jira-project nil
       :issue-types (:issue-types jira-data)
       :issue-type nil
       :summary nil
       :description (-> js/window .-location .-href (str "\n"))})
    om/IWillMount
    (will-mount [_]
      (let [project-name (vcs-url/project-name (:vcs_url project))
            vcs-type (project-model/vcs-type project)]
        (raise! owner [:load-jira-projects {:project-name project-name :vcs-type vcs-type}])
        (raise! owner [:load-jira-issue-types {:project-name project-name :vcs-type vcs-type}])))
    om/IRenderState
    (render-state [_ {:keys [jira-project jira-projects issue-type issue-types summary description]}]
      (let [project-name (vcs-url/project-name (:vcs_url project))
            vcs-type (project-model/vcs-type project)]
        (modal/modal-dialog
          {:title "Create an issue in JIRA"
           :body
           (html
             [:div
              (form/form {}
                         (dropdown/dropdown {:label "Project name"
                                             :name "project-name"
                                             :value jira-project
                                             :options (or (some-> jira-projects
                                                                  (map #(into [% %] nil)))
                                                          [["No projects" "No projects"]])
                                             :on-change #(om/set-state! owner :jira-project (.. % -target -value))})
                         (dropdown/dropdown {:label "Issue type"
                                             :name "issue-type"
                                             :value issue-type
                                             :options (or (some-> issue-types
                                                                  (map #(into [% %] nil)))
                                                          [["No issue types" "No issue types"]])
                                             :on-change #(om/set-state! owner :type (.. % -target -value))})
                         (om/build form/text-field {:label "Issue summary"
                                                    :value summary
                                                    :on-change #(om/set-state! owner :summary (.. % -target -value))})
                         (om/build form/text-area {:label "Description"
                                                   :value description
                                                   :on-change #(om/set-state! owner :description (.. % -target -value))}))])
           :actions [(forms/managed-button
                       [:input.create-jira-issue-button
                        {:data-failed-text "Failed" ,
                         :data-success-text "Created" ,
                         :data-loading-text "Creating..." ,
                         :value "Create" ,
                         :type "submit"
                         :disabled (not (and project type summary description))
                         :on-click #(raise! owner [:create-jira-issue {:project-name project-name
                                                                       :vcs-type vcs-type
                                                                       :jira-issue-data {:jira-project jira-project
                                                                                         :type type
                                                                                         :summary summary
                                                                                         :description description}
                                                                       :on-success close-fn}])}])]
           :close-fn close-fn})))))

