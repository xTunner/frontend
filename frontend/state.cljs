(ns frontend.state)

(defn initial-state []
  {:environment "development"
   :settings {:projects {} ; hash of project-id to settings
              :organizations  {:circleci  {:plan {}}}
              :add-projects {:repo-filter-string ""
                             :selected-org {:login nil
                                            :type :org}}}
   :navigation-point :loading
   :current-user nil
   :crumbs []
   :current-repos []
   :render-context nil
   :projects []
   :recent-builds nil
   :project-settings-subpage nil
   :project-settings-project-name nil
   :org-settings-subpage nil
   :org-settings-org-name nil
   :dashboard-data {:branch nil
                    :repo nil
                    :org nil}
   :current-project-data {:project nil
                          :plan nil
                          :settings {}
                          :tokens nil
                          :envvars nil}
   :current-build-data {:build nil
                        :usage-queue-data {:builds nil
                                           :show-usage-queue false}
                        :artifact-data {:artifacts nil
                                        :show-artifacts false}
                        :current-container-id 0
                        :container-data {:current-container-id 0
                                         :containers nil}
                        :invite-data {:dismiss-invite-form nil
                                      ;; map of login to github user. These could go
                                      ;; in current-project-data, but it would make the
                                      ;; invites implementation more complex. Much easier
                                      ;; for each build to have its own copy of github-users, especially
                                      ;; since it's used so infrequently and goes stale fast.
                                      :github-users nil}}
   :current-organization nil})

(def user-path [:current-user])

(def build-data-path [:current-build-data])
(def build-path [:current-build-data :build])
(def build-github-users-path (conj build-data-path :invite-data :github-users))
(defn build-github-user-path [index] (conj build-github-users-path index))
(def dismiss-invite-form-path (conj build-data-path :invite-data :dismiss-invite-form))
(def dismiss-config-errors-path (conj build-data-path :dismiss-config-errors))
(def invite-logins-path (conj build-data-path :invite-data :invite-logins))
(defn invite-login-path [login] (conj invite-logins-path login))

(def usage-queue-path [:current-build-data :usage-queue-data :builds])
(defn usage-queue-build-path [build-index] (conj usage-queue-path build-index))
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

(def project-data-path [:current-project-data])
(def project-plan-path (conj project-data-path :plan))
(def project-tokens-path (conj project-data-path :tokens))
(def project-envvars-path (conj project-data-path :envvars))
(def project-settings-branch-path (conj project-data-path :settings-branch))
(def project-path (conj project-data-path :project))

(def project-new-ssh-key-path (conj project-data-path :new-ssh-key))
(def project-new-api-token-path (conj project-data-path :new-api-token))

(def crumbs-path [:crumbs])
(defn project-branch-crumb-path [state]
  (let [crumbs (get-in state crumbs-path)
        project-branch-crumb-index (->> crumbs
                                        (keep-indexed
                                          #(when (= (:type %2) :project-branch)
                                             %1))
                                        first)]
    (conj crumbs-path project-branch-crumb-index)))

;; XXX we probably shouldn't be storing repos in the user...
(defn repos-path
  "Path for a given set of repos (e.g. all heavybit repos). Login is the username,
   type is :user or :org"
  [login type] (conj user-path :repos (str login "." type)))

(defn repo-path [login type repo-index]
  (conj (repos-path login type) repo-index))
