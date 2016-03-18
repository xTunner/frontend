(ns frontend.controllers.navigation
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.analytics :as analytics]
            [frontend.async :refer [put!]]
            [frontend.api :as api]
            [frontend.api.path :as api-path]
            [frontend.components.documentation :as docs]
            [frontend.favicon]
            [frontend.models.feature :as feature]
            [frontend.models.build :as build-model]
            [frontend.pusher :as pusher]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils.ajax :as ajax]
            [frontend.utils.docs :as doc-utils]
            [frontend.utils.state :as state-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [frontend.utils :as utils :refer [mlog merror set-page-title! set-page-description! scroll-to-id! scroll!]]
            [frontend.routes :as routes]
            [goog.dom]
            [goog.string :as gstring])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

;; TODO we could really use some middleware here, so that we don't forget to
;;      assoc things in state on every handler
;;      We could also use a declarative way to specify each page.
;; --- Navigation Multimethod Declarations ---

(defmulti navigated-to
  (fn [history-imp navigation-point args state] navigation-point))

(defmulti post-navigated-to!
  (fn [history-imp navigation-point args previous-state current-state]
    (frontend.favicon/reset!)
    (put! (get-in current-state [:comms :ws]) [:unsubscribe-stale-channels])
    navigation-point))

;; --- Navigation Multimethod Implementations ---

(defn navigated-default [navigation-point args state]
  (-> state
      state-utils/clear-page-state
      (assoc :navigation-point navigation-point
             :navigation-data args)))

(defmethod navigated-to :default
  [history-imp navigation-point args state]
  (navigated-default navigation-point args state))

(defn post-default [navigation-point args]
  (set-page-title! (or (:_title args)
                       (str/capitalize (name navigation-point))))
  (when :_description args
        (set-page-description! (:_description args)))
  (scroll! args))

(defmethod post-navigated-to! :default
  [history-imp navigation-point args previous-state current-state]
  (post-default navigation-point args))

(defmethod navigated-to :navigate!
  [history-imp navigation-point args state]
  state)

(defmethod post-navigated-to! :navigate!
  [history-imp navigation-point {:keys [path replace-token?]} previous-state current-state]
  (let [path (if (= \/ (first path))
               (subs path 1)
               path)]
    (if replace-token? ;; Don't break the back button if we want to redirect someone
      (.replaceToken history-imp path)
      (.setToken history-imp path))))


(defmethod navigated-to :dashboard
  [history-imp navigation-point args state]
  (-> state
      state-utils/clear-page-state
      (assoc :navigation-point navigation-point
             :navigation-data args
             :recent-builds nil)
      (state-utils/set-dashboard-crumbs args)
      state-utils/reset-current-build
      state-utils/reset-current-project))

(defmethod post-navigated-to! :dashboard
  [history-imp navigation-point args previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        projects-loaded? (seq (get-in current-state state/projects-path))
        current-user (get-in current-state state/user-path)]
    (mlog (str "post-navigated-to! :dashboard with current-user? " (not (empty? current-user))
               " projects-loaded? " (not (empty? projects-loaded?))))
    (when (and (not projects-loaded?)
               (not (empty? current-user)))
      (api/get-projects api-ch))
    (go (let [builds-url (api/dashboard-builds-url (assoc (:navigation-data current-state)
                                                          :builds-per-page (:builds-per-page current-state)))
              api-resp (<! (ajax/managed-ajax :get builds-url))
              scopes (:scopes api-resp)
              comms (get-in current-state [:comms])]
          (mlog (str "post-navigated-to! :dashboard, " builds-url " scopes " scopes))
          (condp = (:status api-resp)
            :success (put! (:api comms) [:recent-builds :success (assoc api-resp :context args)])
            :failed (put! (:nav comms) [:error {:status (:status-code api-resp) :inner? false}])
            (put! (:errors comms) [:api-error api-resp]))
          (when (:repo args)
            (ajax/ajax :get
                       (gstring/format "/api/v1/project/%s/%s/build-diagnostics" (:org args) (:repo args))
                       :project-build-diagnostics
                       api-ch
                       :context {:project-name (str (:org args) "/" (:repo args))})
            (when (:read-settings scopes)
              (ajax/ajax :get
                         (api-path/project-settings (:vcs_type args) (:org args) (:repo args))
                         :project-settings
                         api-ch
                         :context {:project-name (str (:org args) "/" (:repo args))})
              (ajax/ajax :get
                         (api-path/project-plan (:vcs_type args) (:org args) (:repo args))
                         :project-plan
                         api-ch
                         :context {:project-name (str (:org args) "/" (:repo args))}))))))
  (set-page-title!))

(defmethod post-navigated-to! :build-state
  [history-imp navigation-point args previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (when-not (seq (get-in current-state state/projects-path))
      (api/get-projects api-ch))
    (api/get-build-state api-ch))
  (set-page-title! "Build State"))

(defmethod navigated-to :build
  [history-imp navigation-point {:keys [vcs_type project-name build-num org repo tab] :as args} state]
  (mlog "navigated-to :build with args " args)
  (if (and (= :build (:navigation-point state))
           (not (state-utils/stale-current-build? state project-name build-num)))
    ;; page didn't change, just switched tabs
    (assoc-in state state/build-header-tab-path tab)
    ;; navigated to page, load everything
    (-> state
        state-utils/clear-page-state
        (assoc :navigation-point navigation-point
               :navigation-data (assoc args
                                       :show-aside-menu? false
                                       :show-settings-link? false)
               :project-settings-project-name project-name)
        (assoc-in state/crumbs-path [{:type :dashboard}
                                     {:type :org :username org :vcs_type vcs_type}
                                     {:type :project :username org :project repo :vcs_type vcs_type}
                                     {:type :project-branch :username org :project repo :vcs_type vcs_type}
                                     {:type :build :username org :project repo
                                      :build-num build-num
                                      :vcs_type vcs_type}])
        state-utils/reset-current-build
        (#(if (state-utils/stale-current-project? % project-name)
            (state-utils/reset-current-project %)
            %))
        (assoc-in state/build-header-tab-path tab)
        state-utils/reset-dismissed-osx-usage-level)))

(defn initialize-pusher-subscriptions
  "Subscribe to pusher channels for initial messaging. This subscribes
  us to build messages (`update`, `add-messages` and `test-results`),
  and container messages for container 0 (`new-action`,`update-action`
  and `append-action`). The first two subscriptions[1] remain for the
  whole build. The second channel will be unsubscribed when other
  containers come into view.

  [1] We currently subscribe to both the old single-channel-per-build
  pusher channel, and the new \"@all\" style channel. This should be
  removed as soon it has been rolled in all environments including
  enterprise sites."
  [state parts]
  (let [ws-ch (get-in state [:comms :ws])
        parts (assoc parts :container-index 0)
        subscribe (fn [channel messages]
                    (put! ws-ch [:subscribe {:channel-name channel :messages messages}]))]
    (subscribe (pusher/build-all-channel parts) pusher/build-messages)
    (subscribe (pusher/build-container-channel parts) pusher/container-messages)
    (subscribe (pusher/obsolete-build-channel parts) (concat pusher/build-messages
                                                             pusher/container-messages))))

(defmethod post-navigated-to! :build
  [history-imp navigation-point {:keys [project-name build-num vcs_type] :as args} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        nav-ch (get-in current-state [:comms :nav])
        err-ch (get-in current-state [:comms :errors])
        projects-loaded? (seq (get-in current-state state/projects-path))
        current-user (get-in current-state state/user-path)]
    (mlog (str "post-navigated-to! :build current-user? " (not (empty? current-user))
               " projects-loaded? " (not (empty? projects-loaded?))))
    (when (and (not projects-loaded?)
               (not (empty? current-user)))
      (api/get-projects api-ch))
    (go (let [build-url (gstring/format "/api/dangerzone/project/%s/%s/%s" vcs_type project-name build-num)
              api-result (<! (ajax/managed-ajax :get build-url))
              build (:resp api-result)
              scopes (:scopes api-result)
              navigation-data (:navigation-data current-state)
              vcs-type (:vcs_type navigation-data)
              org (:org navigation-data)
              repo (:repo navigation-data)
              plan-url (case vcs_type
                         "github" (gstring/format "/api/v1/project/%s/plan" project-name)
                         "bitbucket" (gstring/format "/api/dangerzone/project/%s/%s/plan" vcs_type project-name))]
          (mlog (str "post-navigated-to! :build, " build-url " scopes " scopes))
          ;; Start 404'ing on non-existent builds, as well as when you
          ;; try to go to a build page of a project which doesn't
          ;; exist. This is different than current behaviour, where
          ;; you see the "regular" inner page, with an error message
          ;; where the build info would be. Thoughts?
          (condp = (:status api-result)
            :success (put! api-ch [:build (:status api-result) (assoc api-result :context {:project-name project-name :build-num build-num})])
            :failed (put! nav-ch [:error {:status (:status-code api-result) :inner? false}])
            (put! err-ch [:api-error api-result]))
          (when (= :success (:status api-result))
            (analytics/track {:event-type :view-build
                              :current-state current-state
                              :build build}))
          ;; Preemptively make the usage-queued API call if the build is in the
          ;; usage queue and the user has access to the info
          (when (and (:read-settings scopes) (build-model/in-usage-queue? build))
            (api/get-usage-queue build api-ch))
          (when (and (not (get-in current-state state/project-path))
                     (:repo args) (:read-settings scopes))
            (ajax/ajax :get
                       (api-path/project-settings vcs-type org repo)
                       :project-settings
                       api-ch
                       :context {:project-name project-name
                                 :vcs-type vcs_type}))
          (when (and (not (get-in current-state state/project-plan-path))
                     (:repo args) (:read-settings scopes))
            (ajax/ajax :get
                       plan-url
                       :project-plan
                       api-ch
                       :context {:project-name project-name
                                 :vcs-type vcs_type}))
          (when (build-model/finished? build)
            (api/get-build-tests build api-ch))))
    (initialize-pusher-subscriptions current-state {:project-name project-name
                                                    :build-num build-num
                                                    :vcs-type vcs_type}))
  (set-page-title! (str project-name " #" build-num)))

(defmethod navigated-to :add-projects
  [history-imp navigation-point args state]
  (-> state
      state-utils/clear-page-state
      (assoc :navigation-point navigation-point
             :navigation-data (assoc args :show-aside-menu? false))
      ;; force a reload of repos.
      (assoc-in state/repos-path [])
      (assoc-in state/github-repos-loading-path true)
      (assoc-in state/bitbucket-repos-loading-path true)
      (assoc-in state/crumbs-path [{:type :add-projects}])
      (assoc-in state/add-projects-selected-org-path nil)
      (state-utils/reset-current-org)))

(defmethod post-navigated-to! :add-projects
  [history-imp navigation-point _ previous-state current-state]
  (println "making api requests.")
  (let [api-ch (get-in current-state [:comms :api])]
    ;; load orgs, collaborators, and repos.
    (api/get-orgs api-ch)
    (api/get-github-repos api-ch)
    (when (feature/enabled? :bitbucket)
      (api/get-bitbucket-repos api-ch)))
  (set-page-title! "Add projects"))

(defmethod navigated-to :build-insights
  [history-imp navigation-point args state]
  (-> state
      (assoc :navigation-point navigation-point
             :navigation-data (assoc args :show-aside-menu? false))
      state-utils/clear-page-state
      (assoc-in state/crumbs-path [{:type :build-insights}])))

(defmethod post-navigated-to! :build-insights
  [history-imp navigation-point _ previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (api/get-projects api-ch)
    (api/get-user-plans api-ch))
  (set-page-title! "Insights"))

(defmethod navigated-to :project-insights
  [history-imp navigation-point {:keys [org repo branch vcs_type] :as args} state]
  (-> state
      (assoc :navigation-point navigation-point
             :navigation-data (assoc args :show-aside-menu? false))
      state-utils/clear-page-state
      (assoc-in state/crumbs-path [{:type :build-insights}
                                   {:type :org
                                    :username org
                                    :vcs_type vcs_type}
                                   {:type :project
                                    :username org
                                    :project repo
                                    :vcs_type vcs_type}
                                   {:type :project-branch
                                    :username org
                                    :branch branch
                                    :project repo
                                    :vcs_type vcs_type}])))

(defmethod post-navigated-to! :project-insights
  [history-imp navigation-point args previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (api/get-projects api-ch)
    (api/get-user-plans api-ch))
  (set-page-title! "Insights"))

(defmethod navigated-to :invite-teammates
  [history-imp navigation-point args state]
  (-> state
      state-utils/clear-page-state
      (assoc :navigation-point navigation-point
             :navigation-data (assoc args :show-aside-menu? false))
      (assoc-in [:invite-data :org] (:org args))
      (assoc-in state/crumbs-path [{:type :invite-teammates}])))

(defmethod post-navigated-to! :invite-teammates
  [history-imp navigation-point args previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        org (:org args)]
    ; get the list of orgs
    (go (let [api-result (<! (ajax/managed-ajax :get "/api/v1/user/organizations"))]
      (put! api-ch [:organizations (:status api-result) api-result])))
    (when org
      (go (let [api-result (<! (ajax/managed-ajax :get (gstring/format "/api/v1/organization/%s/members" org)))]
            (put! api-ch [:org-member-invite-users (:status api-result) api-result]))))
    (set-page-title! "Invite teammates")))

(defmethod navigated-to :project-settings
  [history-imp navigation-point {:keys [project-name subpage org repo vcs_type] :as args} state]
  (-> state
      state-utils/clear-page-state
      (assoc :navigation-point navigation-point
             :navigation-data args
             ;; TODO can we get rid of project-settings-subpage in favor of navigation-data?
             :project-settings-subpage subpage
             :project-settings-project-name project-name)
      (assoc-in state/crumbs-path [{:type :settings-base}
                                   {:type :org
                                    :username org
                                    :vcs_type vcs_type}
                                   {:type :project
                                    :username org
                                    :project repo
                                    :vcs_type vcs_type}])
      (#(if (state-utils/stale-current-project? % project-name)
          (state-utils/reset-current-project %)
          %))))

(defmethod post-navigated-to! :project-settings
  [history-imp navigation-point {:keys [project-name vcs_type subpage]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])
        navigation-data (:navigation-data current-state)
        vcs-type (:vcs_type navigation-data)
        org (:org navigation-data)
        repo (:repo navigation-data)]
    (when-not (seq (get-in current-state state/projects-path))
      (api/get-projects api-ch))
    (if (get-in current-state state/project-path)
      (mlog "project settings already loaded for" project-name)
      (ajax/ajax :get
                 (api-path/project-settings vcs-type org repo)
                 :project-settings
                 api-ch
                 :context {:project-name project-name}))

    (cond (and (= subpage :parallel-builds)
               (not (get-in current-state state/project-plan-path)))
          (ajax/ajax :get
                     (api-path/project-plan vcs-type org repo)
                     :project-plan
                     api-ch
                     :context {:project-name project-name})

          (and (= subpage :checkout)
               (not (get-in current-state state/project-checkout-keys-path)))
          (ajax/ajax :get
                     (api-path/project-checkout-keys vcs-type project-name)
                     :project-checkout-key
                     api-ch
                     :context {:project-name project-name})

          (and (#{:api :badges} subpage)
               (not (get-in current-state state/project-tokens-path)))
          (ajax/ajax :get
                     (gstring/format "/api/v1/project/%s/token" project-name)
                     :project-token
                     api-ch
                     :context {:project-name project-name})

          (and (= subpage :env-vars)
               (not (get-in current-state state/project-envvars-path)))
          (ajax/ajax :get
                     (case vcs_type
                       "github" (gstring/format "/api/v1/project/%s/envvar" project-name)
                       "bitbucket" (gstring/format "/api/dangerzone/project/%s/%s/envvar" vcs_type project-name))
                     :project-envvar
                     api-ch
                     :context {:project-name project-name})

          (= subpage :code-signing)
          (api/get-project-code-signing-keys project-name api-ch)

          :else nil))

  (set-page-title! (str "Project settings - " project-name)))

(defmethod post-navigated-to! :landing
  [history-imp navigation-point _ previous-state current-state]
  (set-page-title! "Continuous Integration and Deployment")
  (set-page-description! "Free Hosted Continuous Integration and Deployment for web and mobile applications. Build better apps and ship code faster with CircleCI."))


(defmethod navigated-to :org-settings
  [history-imp navigation-point {:keys [subpage org vcs_type] :as args} state]
  (mlog "Navigated to subpage:" subpage)

  (-> state
      state-utils/clear-page-state
      (assoc :navigation-point navigation-point)
      (assoc :navigation-data args)
      (assoc-in state/org-settings-subpage-path subpage)
      (assoc-in state/org-settings-org-name-path org)
      (assoc-in state/org-settings-vcs-type-path vcs_type)
      (assoc-in state/crumbs-path [{:type :settings-base}
                                   {:type :org
                                    :username org}])
      (#(if (state-utils/stale-current-org? % org)
          (state-utils/reset-current-org %)
          %))))

(defmethod post-navigated-to! :org-settings
  [history-imp navigation-point {:keys [vcs_type org subpage]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (when-not (seq (get-in current-state state/projects-path))
      (api/get-projects api-ch))
    (if (get-in current-state state/org-plan-path)
      (mlog "plan details already loaded for" org)
      ;; Only GitHub orgs support paid plans currently.
      (when (= "github" vcs_type)
        (api/get-org-plan org api-ch)))
    (if (= org (get-in current-state state/org-name-path))
      (mlog "organization details already loaded for" org)
      (api/get-org-settings vcs_type org api-ch))
    (condp = subpage
      :organizations (ajax/ajax :get "/api/v1/user/organizations" :organizations api-ch)
      :billing (do
                 (ajax/ajax :get
                            (gstring/format "/api/v1/organization/%s/card" org)
                            :plan-card
                            api-ch
                            :context {:org-name org})
                 (ajax/ajax :get
                            (gstring/format "/api/v1/organization/%s/invoices" org)
                            :plan-invoices
                            api-ch
                            :context {:org-name org}))
      nil))
  (set-page-title! (str "Org settings - " org)))

(defmethod navigated-to :logout
  [history-imp navigation-point _ state]
  (state-utils/clear-page-state state))

(defmethod post-navigated-to! :logout
  [history-imp navigation-point _ previous-state current-state]
  (go (let [api-result (<! (ajax/managed-ajax :post "/logout"))]
        (set! js/window.location "/"))))


(defmethod navigated-to :error
  [history-imp navigation-point {:keys [status] :as args} state]
  (let [orig-nav-point (get-in state [:navigation-point])]
    (mlog "navigated-to :error with (:navigation-point state) of " orig-nav-point)
    (-> state
        state-utils/clear-page-state
        (assoc :navigation-point navigation-point
               :navigation-data args
               :original-navigation-point orig-nav-point))))

(defmethod post-navigated-to! :error
  [history-imp navigation-point {:keys [status] :as args} previous-state current-state]
  (set-page-title! (condp = status
                     401 "Login required"
                     404 "Page not found"
                     500 "Internal server error"
                     "Something unexpected happened")))

(defmethod navigated-to :account
  [history-imp navigation-point {:keys [subpage] :as args} state]
  (mlog "Navigated to account subpage:" subpage)
  (let [logged-in? (get-in state state/user-path)
        nav-ch (get-in state [:comms :nav])]
    (if logged-in?
       (-> state
           state-utils/clear-page-state
           (assoc :navigation-point navigation-point
                  :navigation-data (assoc args :show-aside-menu? false))
           (assoc :account-settings-subpage subpage)
           (assoc-in state/crumbs-path [{:type :account}]))
       (do
         (routes/open-to-outer! nav-ch :error {:status 401})
         state))))

(defmethod post-navigated-to! :account
  [history-imp navigation-point {:keys [org-name subpage]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (when-not (seq (get-in current-state state/projects-path))
      (api/get-projects (get-in current-state [:comms :api])))
    (ajax/ajax :get "/api/v1/sync-github" :me api-ch)
    (ajax/ajax :get "/api/v1/user/organizations" :organizations api-ch)
    (ajax/ajax :get "/api/v1/user/token" :tokens api-ch)
    (set-page-title! "Account")))

(defmethod post-navigated-to! :documentation
  [history-imp navigation-point {:keys [subpage] :as params} previous-state current-state]
  (go
    (let [api-ch (get-in current-state [:comms :api])
          nav-ch (get-in current-state [:comms :nav])
          docs (or (get-in current-state state/docs-data-path)
                   (let [api-result (<! (ajax/managed-ajax :get (get-in current-state [:render-context :doc_manifest_url]) :csrf-token false))]
                     (put! api-ch [:doc-manifest (:status api-result) api-result])
                     (when (= :success (:status api-result))
                       (doc-utils/format-doc-manifest (:resp api-result)))))
          doc (get docs subpage)]
      (cond
       (not subpage) (set-page-title! "What can we help you with?")
       doc
       (do
         (set-page-title! (:title doc))
         (set-page-description! (:description doc))
         (scroll! params)
         (when (and (empty? (:children doc))
                    (not (:markdown doc)))
           (let [url (-> "/docs/%s.md"
                         (gstring/format (name subpage))
                         stefon/asset-path)]
             (ajax/ajax :get url :doc-markdown api-ch :context {:subpage subpage} :format :raw))))
       (= subpage :search)
       (do
         (set-page-title! "Doc Search"))
       :else
       (let [token (str (name subpage) (when (:_fragment params) (str "#" (:_fragment params))))
             rewrite-token (doc-utils/maybe-rewrite-token token)
             path (if (= token rewrite-token)
                    "/docs"
                    (str "/docs" (when-not (str/blank? rewrite-token) (str "/" rewrite-token))))]
         (put! nav-ch [:navigate! {:path path :replace-token? true}]))))))

(defmethod post-navigated-to! :language-landing
  [history-imp navigation-point {:keys [language] :as args} previous-state current-state]
  (let [titles {:ruby "Ruby and Ruby on Rails CI Support"
                :python "Python Continuous Integration"}
        descriptions {:ruby "CircleCI makes Continuous Integration for your Ruby project simple and easy. Get started testing for free!"
                      :python "Get started for free with Continuous Integration and Deployment for your Python projects. CircleCI integrates with any language."}]
    (when-let [title (get titles language)]
      (set-page-title! title))
    (when-let [description (get descriptions language)]
      (set-page-description! description))))

(defmethod post-navigated-to! :integrations
  [history-imp navigation-point {:keys [integration] :as args} previous-state current-state]
  (let [titles {:docker "Integration with Docker Containers"
                :heroku "Integrate with Heroku Deployment"
                :saucelabs "Sauce Labs Browser Testing"}
        descriptions {:docker "CircleCI integrates with your Docker container so you can easily build, run, and ship your applications anywhere."
                      :heroku "CircleCI integrates seamlessly with Heroku to provide a simple continuous delivery workflow for your organization."
                      :saucelabs "Integrate with SauceLabs to test against hundreds of desktop and mobile browsers with Selenium WebDriver to get rid of your browser bugs."}]
    (set-page-title! (get titles integration))
    (set-page-description! (get descriptions integration))))

(defmethod post-navigated-to! :stories
  [history-imp navigation-point {:keys [story] :as args} previous-state current-state]
  (let [titles {:shopify "Shopify's Success with Continuous Integration"
                :wit "Wit.ai's Success with Continuous Integration"}
        descriptions {:shopify "See how Shopify has scaled its Continuous Integration efforts with CircleCI and made its developer team of over 130 people more efficient."
                      :wit ""}]
    (set-page-title! (get titles story))
    (set-page-description! (get descriptions story))))

(defmethod navigated-to :admin-settings
  [history-imp navigation-point {:keys [subpage] :as args} state]
  (-> state
      state-utils/clear-page-state
      (assoc :navigation-point navigation-point
             :navigation-data args
             :admin-settings-subpage subpage
             :recent-builds nil)))

(defmethod post-navigated-to! :admin-settings
  [history-imp navigation-point {:keys [subpage tab]} previous-state current-state]
  (let [api-ch (get-in current-state [:comms :api])]
    (case subpage
      :fleet-state (do
                     (api/get-fleet-state api-ch)
                     (api/get-admin-dashboard-builds tab api-ch)
                     (set-page-title! "Fleet State"))
      :license (set-page-title! "License")
      :users (do
               (api/get-all-users api-ch)
               (set-page-title! "Users"))
      :system-settings (do
                         (api/get-all-system-settings api-ch)
                         (set-page-title! "System Settings")))))
