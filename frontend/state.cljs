(ns frontend.state)

(defn initial-state []
  {:environment "development"
   :settings {:projects {}            ; hash of project-id to settings
              :organizations  {:circleci  {:plan {}}}
              :add-projects {:repo-filter-string ""
                             :selected-org {:login nil
                                            :type :org}}}
   :navigation-point :loading
   :navigation-data nil
   :navigation-settings {}
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
   :current-org-data {:plan nil
                      :projects nil
                      :users nil
                      :name nil}
   :instrumentation []})

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
(def user-organizations-path (conj user-path :organizations))
(def user-collaborators-path (conj user-path :collaborators))

(defn repos-path
  "Path for a given set of repos (e.g. all heavybit repos). Login is the username,
   type is :user or :org"
  [login type] (conj user-path :repos (str login "." type)))

(defn repo-path [login type repo-index]
  (conj (repos-path login type) repo-index))


(def org-data-path [:current-org-data])
(def org-name-path (conj org-data-path :name))
(def org-plan-path (conj org-data-path :plan))
(def stripe-card-path (conj org-data-path :card))
(def org-users-path (conj org-data-path :users))
(def org-projects-path (conj org-data-path :projects))
(def org-loaded-path (conj org-data-path :loaded))
(def org-authorized?-path (conj org-data-path :authorized?))
(def selected-containers-path (conj org-data-path :selected-containers))
;; Map of org login to boolean (selected or not selected)
(def selected-piggyback-orgs-path (conj org-data-path :selected-piggyback-orgs))

(def settings-path [:settings])

(def projects-path [:projects])

;; XXX make inner/outer something defined in navigation
(def inner?-path [:current-user])

(def show-nav-settings-link-path [:navigation-settings :show-settings-link])

(def instrumentation-path [:instrumentation])

(def browser-settings-path [:settings :browser-settings])
(def show-instrumentation-line-items-path (conj browser-settings-path :show-instrumentation-line-items))
(def show-admin-panel-path (conj browser-settings-path :show-admin-panel))
(def slim-aside-path (conj browser-settings-path :slim-aside?))
(def show-all-branches-path (conj browser-settings-path :show-all-branches))
(defn project-branches-collapsed-path [project-id] (conj browser-settings-path :projects project-id :branches-collapsed))
(def show-inspector-path (conj browser-settings-path :show-inspector))

(def flash-path [:render-context :flash])
(def account-subpage-path [:account-settings-subpage])
