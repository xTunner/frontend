(ns frontend.components.setup-project
  (:require [frontend.async :refer [raise!]]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.dropdown :as dropdown]
            [frontend.components.pieces.spinner :refer [spinner]]
            [frontend.models.repo :as repo-model]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [defrender html]]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]))

(defn- projects-dropdown [{:keys [projects selected-project]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:.projects-dropdown
         [:h4 "Repository"]
         (if (nil? projects)
           [:.spinner-placeholder (spinner)]
           (dropdown/dropdown
             {:label "Repo"
              :on-change #(raise! owner [:setup-project-select-project %])
              :default-text "Select a repository"
              :value (repo-model/id selected-project)
              :options (->> projects
                            vals
                            (sort-by #(vcs-url/project-name (:vcs_url %)))
                            (map (fn [repo]
                                   (let [repo-id (repo-model/id repo)]
                                     [repo-id (vcs-url/project-name (:vcs_url repo))]))))}))]))))

(defrender setup-project [data owner]
  (let [projects (get-in data state/setup-project-projects-path)
        selected-project (get-in data state/setup-project-selected-project-path)]
    (html
      (card/basic
        (html
          [:div
           [:h1 "Setup Project"]
           [:p "CircleCI helps you ship better code, faster. Let's add some projects on CircleCI. To kick things off, you'll need to choose a project to build. We'll start a new build for you each time someone pushes a new commit."]
           [:p "Know what you are doing? " [:a {:href "#"} "Read Documentation"]]
           (om/build projects-dropdown {:projects projects
                                        :selected-project selected-project})])))))
