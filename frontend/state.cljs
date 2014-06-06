(ns frontend.state)

(defn initial-state []
  {:environment "development"
   :settings {:projects {}            ; hash of project-id to settings
              :add-projects {:repo-filter-string ""
                             :selected-org {:login nil
                                            :type :org}}}
   :navigation-point :loading
   :current-user nil
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
   :render-context nil
   :projects []
   :recent-builds nil
   :project-settings-subpage nil
   :project-settings-project-name nil
   :current-project nil
   :current-build-data {:build nil
                        :usage-queue-data {:builds nil
                                           :show-usage-queue false}
                        :artifact-data {:artifacts nil
                                        :show-artifacts false}
                        :current-container-id 0
                        :container-data {:current-container-id 0
                                         :containers nil}
                        :invite-data {:dismiss-invite-form nil
                                      ;; XXX should github-users go in project?
                                      ;; map of login to github user
                                      :github-users nil
                                      ;; map of logins to checked status
                                      :invite-logins {}}}
   :current-organization nil})


(def build-data-path [:current-build-data])
(def build-path [:current-build-data :build])
(def build-github-users-path (conj build-data-path :invite-data :github-users))
(defn build-github-user-path [index] (conj build-github-users-path index))
(def dismiss-invite-form-path (conj build-data-path :invite-data :dismiss-invite-form))
(def invite-logins-path (conj build-data-path :invite-data :invite-logins))
(defn invite-login-path [login] (conj invite-logins-path login))

(def usage-queue-path [:current-build-data :usage-queue-data :builds])
(def show-usage-queue-path [:current-build-data :usage-queue-data :show-usage-queue])

(def artifacts-path [:current-build-data :artifacts-data :artifacts])
(def show-artifacts-path [:current-build-data :artifacts-data :show-artifacts])

(def container-data-path [:current-build-data :container-data])
(def containers-path [:current-build-data :container-data :containers])
(def current-container-path [:current-build-data :container-data :current-container-id])
(defn container-path [container-index] (conj containers-path container-index))
(defn actions-path [container-index] (conj (container-path container-index) :actions))
(defn action-path [container-index action-index] (conj (actions-path container-index) action-index))
(defn action-output-path [container-index action-index] (conj (action-path container-index action-index) :output))
(defn show-action-output-path [container-index action-index] (conj (action-path container-index action-index) :show-output))

(defn reset-current-build [state]
  (assoc state :current-build-data {:build nil
                                    :usage-queue-data {:builds nil
                                                       :show-usage-queue false}
                                    :artifact-data {:artifacts nil
                                                    :show-artifacts false}
                                    :current-container-id 0
                                    :container-data {:current-container-id 0
                                                     :containers nil}}))
