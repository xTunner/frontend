(ns frontend.state)

(def debug-state)

(defn initial-state []
  {:error-message nil
   :general-message nil
   ;; A/B test instructions:
   ;; 1. Don't define a test with null as one of the options
   ;; 2. If you change a test's options, you must also change the test's name
   ;; 3. Record your tests here: https://docs.google.com/a/circleci.com/spreadsheet/ccc?key=0AiVfWAkOq5p2dE1MNEU3Vkw0Rk9RQkJNVXIzWTAzUHc&usp=sharing

   ;; Please kebab-case and not snak_case tests and treatments
   :ab-test-definitions {:a-is-a [true false]
                         :multi-test-equal-variants [:a :b :c :d]
                         :auth-button-vs-page [:button :page]
                         ;; TODO: The below are ab tests that have been running since December 2014. We should figure out if they are being
                         ;; tracked, which are the winners, launch them, and delete the dead code.
                         :pay_now_button [true false]
                         :follow_notice [true false]
                         :new_usage_queued_upsell [true false]}
   :environment "development"
   :settings {:projects {}            ; hash of project-id to settings
              :organizations  {:circleci  {:plan {}}}
              :add-projects {:repo-filter-string ""
                             :selected-org {:login nil
                                            :type :org}
                             :show-forks true}
              :browser-settings {:expanded-repos #{}}}
   :selected-home-technology-tab nil
   :modal-video-id nil
   :builds-per-page 30
   :navigation-point nil
   :navigation-data nil
   :navigation-settings {}
   :current-user nil
   :crumbs nil
   :current-repos []
   :render-context nil
   :projects nil
   :recent-builds nil
   :project-settings-subpage nil
   :project-settings-project-name nil
   :org-settings {:subpage nil
                  :org-name nil
                  :vcs_type nil}
   :admin-settings-subpage nil
   :dashboard-data {:branch nil
                    :repo nil
                    :org nil}
   :current-project-data {:project nil
                          :plan nil
                          :build-diagnostics nil
                          :settings {}
                          :tokens nil
                          :checkout-keys nil
                          :envvars nil}
   :current-org-data {:plan nil
                      :projects nil
                      :users nil
                      :invoices nil
                      :name nil}
   :invite-data {:dismiss-invite-form nil
                 :github-users nil}
   :instrumentation []
   :hamburger-menu "closed"
   ;; This isn't passed to the components, it can be accessed though om/get-shared :_app-state-do-not-use
   :inputs nil
   :insights {:selected-filter :all
              :selected-sorting :alphabetical}})

(def user-path [:current-user])

(def build-data-path [:current-build-data])
(def build-path [:current-build-data :build])
(def invite-github-users-path [:invite-data :github-users])
(defn invite-github-user-path [index] (conj invite-github-users-path index))
(def dismiss-invite-form-path [:invite-data :dismiss-invite-form])
(def dismiss-config-errors-path (conj build-data-path :dismiss-config-errors))
(def invite-logins-path (conj build-data-path :invite-data :invite-logins))
(defn invite-login-path [login] (conj invite-logins-path login))

(def usage-queue-path [:current-build-data :usage-queue-data :builds])
(defn usage-queue-build-path [build-index] (conj usage-queue-path build-index))
(def show-usage-queue-path [:current-build-data :usage-queue-data :show-usage-queue])

(def artifacts-path [:current-build-data :artifacts-data :artifacts])
(def show-artifacts-path [:current-build-data :artifacts-data :show-artifacts])

(def tests-path [:current-build-data :tests-data :tests])

(def show-config-path [:current-build-data :config-data :show-config])

(def container-data-path [:current-build-data :container-data])
(def containers-path [:current-build-data :container-data :containers])
(def current-container-path [:current-build-data :container-data :current-container-id])
(def current-container-filter-path [:current-build-data :container-data :current-filter])
(def container-paging-offset-path [:current-build-data :container-data :paging-offset])
(def build-header-tab-path [:current-build-data :selected-header-tab])

(def org-settings-path [:org-settings])
(def org-settings-subpage-path (conj org-settings-path :subpage))
(def org-settings-org-name-path (conj org-settings-path :org-name))
(def org-settings-vcs-type-path (conj org-settings-path :vcs_type))

(defn container-path [container-index] (conj containers-path container-index))
(defn actions-path [container-index] (conj (container-path container-index) :actions))
(defn action-path [container-index action-index] (conj (actions-path container-index) action-index))
(defn action-output-path [container-index action-index] (conj (action-path container-index action-index) :output))
(defn show-action-output-path [container-index action-index] (conj (action-path container-index action-index) :show-output))

(def project-data-path [:current-project-data])
(def project-plan-path (conj project-data-path :plan))
(def project-build-diagnostics-path (conj project-data-path :build-diagnostics))
(def project-tokens-path (conj project-data-path :tokens))
(def project-checkout-keys-path (conj project-data-path :checkout-keys))
(def project-envvars-path (conj project-data-path :envvars))
(def project-settings-branch-path (conj project-data-path :settings-branch))
(def project-path (conj project-data-path :project))
(def project-scopes-path (conj project-data-path :project-scopes))
(def page-scopes-path [:page-scopes])
(def project-osx-keys-path (conj project-data-path :osx-keys))

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

;; TODO we probably shouldn't be storing repos in the user...
(def user-login-path (conj user-path :login))
(def user-organizations-path (conj user-path :organizations))
(def user-plans-path (conj user-path :plans))
(def user-tokens-path (conj user-path :tokens))
(def user-analytics-id-path (conj user-path :analytics_id))

(def repos-path (conj user-path :repos))
(defn repo-path [repo-index] (conj repos-path repo-index))

(def github-repos-loading-path (conj user-path :repos-loading :github))
(def bitbucket-repos-loading-path (conj user-path :repos-loading :bitbucket))

(def user-email-prefs-key :basic_email_prefs)
(def user-email-prefs-path (conj user-path :basic_email_prefs))
(def user-selected-email-key :selected_email)
(def user-selected-email-path (conj user-path user-selected-email-key))

(def user-in-beta-key :in_beta_program)
(def user-in-beta-path (conj user-path user-in-beta-key))
(def user-betas-key :enrolled_betas)
(def user-betas-path (conj user-path user-betas-key))

(def org-data-path [:current-org-data])
(def org-name-path (conj org-data-path :name))
(def org-vcs_type-path (conj org-data-path :vcs_type))
(def org-plan-path (conj org-data-path :plan))
(def org-osx-enabled-path (conj org-data-path :osx_builds_enabled?))
(def org-plan-balance-path (conj org-plan-path :account_balance))
(def stripe-card-path (conj org-data-path :card))
(def org-users-path (conj org-data-path :users))
(def org-projects-path (conj org-data-path :projects))
(def org-loaded-path (conj org-data-path :loaded))
(def org-authorized?-path (conj org-data-path :authorized?))
(def selected-containers-path (conj org-data-path :selected-containers))
;; Map of org login to boolean (selected or not selected)
(def selected-piggyback-orgs-path (conj org-data-path :selected-piggyback-orgs))
(def selected-transfer-org-path (conj org-data-path :selected-transfer-org))
(def org-invoices-path (conj org-data-path :invoices))
(def selected-cancel-reasons-path (conj org-data-path :selected-cancel-reasons))
;; Map of reason to boolean (selected or not selected)
(defn selected-cancel-reason-path [reason] (conj selected-cancel-reasons-path reason))
(def cancel-notes-path (conj org-data-path :cancel-notes))

(def settings-path [:settings])

(def projects-path [:projects])

(def inner?-path [:navigation-data :inner?])
(def navigation-repo-path [:navigation-data :repo])
(def navigation-org-path [:navigation-data :org])

(def instrumentation-path [:instrumentation])

(def browser-settings-path [:settings :browser-settings])
(def show-instrumentation-line-items-path (conj browser-settings-path :show-instrumentation-line-items))
(def show-admin-panel-path (conj browser-settings-path :show-admin-panel))
(def show-all-branches-path (conj browser-settings-path :show-all-branches))
(def expanded-repos-path (conj browser-settings-path :expanded-repos))
(def sort-branches-by-recency-path (conj browser-settings-path :sort-branches-by-recency))
(defn project-branches-collapsed-path [project-id] (conj browser-settings-path :projects project-id :branches-collapsed))
(defn project-build-diagnostics-collapsed-path [project-id] (conj browser-settings-path :projects project-id :build-diagnostics-collapsed))
(def show-inspector-path (conj browser-settings-path :show-inspector))
(def statuspage-dismissed-update-path (conj browser-settings-path :statuspage-dismissed-update))
(def logging-enabled-path (conj browser-settings-path :logging-enabled))
(def dismissed-osx-usage-level (conj browser-settings-path :dismissed-osx-usage-level))

(def add-projects-settings-path (conj settings-path :add-projects))
(def add-projects-selected-org-path (conj add-projects-settings-path :selected-org))
(def add-projects-selected-org-login-path (conj add-projects-selected-org-path :login))

(def account-subpage-path [:account-settings-subpage])
(def new-user-token-path (conj user-path :new-user-token))

(def flash-path [:render-context :flash])

(def error-data-path [:error-data])

(def selected-home-technology-tab-path [:selected-home-technology-tab])

(def modal-video-id-path [:modal-video-id])

(def language-testimonial-tab-path [:selected-language-testimonial-tab])

(def build-state-path [:build-state])

(def fleet-state-path [:build-system :builders])
(def build-system-summary-path [:build-system :queue-and-build-counts])

(def license-path [:render-context :enterprise_license])

(def all-users-path [:all-users])

(def error-message-path [:error-message])
(def general-message-path [:general-message])

(def inputs-path [:inputs])

(def docs-data-path [:docs-data])
(def docs-search-path [:docs-query])
(def docs-articles-results-path [:docs-articles-results])
(def docs-articles-results-query-path [:docs-articles-results-query])

(def customer-logo-customer-path [:customer-logo-customer])

(def selected-toolset-path [:selected-toolset])

(def pricing-parallelism-path [:pricing-parallelism])

(def top-nav-orgs-path [:top-nav :orgs])
(def top-nav-selected-org-path [:top-nav :selected-org])
(def hamburger-menu-path [:hamburger-menu])

(def insights-filter-path [:insights :selected-filter])
(def insights-sorting-path [:insights :selected-sorting])

(def current-view-path [:navigation-point])
