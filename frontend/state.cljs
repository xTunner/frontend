(ns frontend.state)

(defn initial-state []
  {:environment "development"
   :settings {:projects {} ; hash of project-id to settings
              :organizations  {:circleci  {:plan {}}}
              :add-projects {:repo-filter-string ""
                             :selected-org {:login nil
                                            :type :org}}}
   :navigation-point :loading
   :current-user {}
   :crumbs [{:type :org
             :name "circleci"
             :path "/gh/circleci"}
            {:type :project
             :name "circle"
             :active true
             :path "/gh/circleci/circle"}
            {:type :settings
             :name "Edit settings"
             :path "/gh/circleci/circle/edit"}]
   :current-repos []
   :render-context {}
   :projects []
   :recent-builds nil
   :project-settings-subpage nil
   :project-settings-project-name nil
   :org-settings-subpage nil
   :org-settings-org-name nil
   :current-project nil
   :current-organization nil})
