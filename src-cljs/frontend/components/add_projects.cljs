(ns frontend.components.add-projects
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as string]
            [frontend.async :refer [raise!]]
            [frontend.analytics :as analytics]
            [frontend.components.common :as common]
            [frontend.components.forms :refer [managed-button]]
            [frontend.config :as config]
            [frontend.datetime :as datetime]
            [frontend.models.feature :as feature]
            [frontend.models.organization :as organization]
            [frontend.models.repo :as repo-model]
            [frontend.models.user :as user-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [inspect]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.bitbucket :as bitbucket]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring]
            [goog.string.format]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [frontend.models.plan :as pm])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [html defrender]]))

(def view "add-projects")

(defn vcs-github? [item] (contains? #{"github" nil} (:vcs_type item)))
(defn vcs-bitbucket? [item] (= "bitbucket" (:vcs_type item)))

(defn missing-scopes-notice [current-scopes missing-scopes]
  [:div
   [:div.alert.alert-error
    "We don't have all of the GitHub OAuth scopes we need to run your tests."
    [:a {:href (gh-utils/auth-url (concat missing-scopes current-scopes))}
     (gstring/format "Click to grant Circle the %s %s."
                     (string/join "and " missing-scopes)
                     (if (< 1 (count missing-scopes)) "scope" "scopes"))]]])

(defn organization [org settings owner]
  (let [login (:login org)
        type (if (:org org) :org :user)
        vcs-type (:vcs_type org)
        selected-org-view {:login login :type type :vcs-type vcs-type}]
    [:li.organization {:on-click #(raise! owner [:selected-add-projects-org selected-org-view])
                       :class (when (= selected-org-view (get-in settings [:add-projects :selected-org])) "active")}
     [:img.avatar {:src (gh-utils/make-avatar-url org :size 50)
            :height 50}]
     [:div.orgname login]]))

(defn missing-org-info
  "A message explaining how to enable organizations which have disallowed CircleCI on GitHub."
  [owner]
  [:p
   "Are you missing an organization? You or an admin may need to enable CircleCI for your organization in "
   [:a.gh_app_permissions {:href (gh-utils/third-party-app-restrictions-url) :target "_blank"}
    "GitHub's application permissions"]
   ". "
   [:a {:on-click #(raise! owner [:refreshed-user-orgs {}]) ;; TODO: spinner while working?
                      :class "active"}
    "Refresh this list"]
   " after you have updated permissions."])

(defn organization-listing [data owner]
  (reify
    om/IDisplayName (display-name [_] "Organization Listing")
    om/IDidMount
    (did-mount [_]
      (utils/tooltip "#collaborators-tooltip-hack" {:placement "right"}))
    om/IRender
    (render [_]
      (let [{:keys [user settings repos]} data]
        (html
         [:div
          [:div.overview
           [:span.big-number "1"]
           [:div.instruction "Choose a GitHub account that you are a member of or have access to."]]
          [:div.organizations
           [:h4 "Your accounts"]
           [:ul.organizations
            (map (fn [org] (organization org settings owner))
                 ;; here we display you, then all of your organizations, then all of the owners of
                 ;; repos that aren't organizations and aren't you. We do it this way because the
                 ;; organizations route is much faster than the repos route. We show them
                 ;; in this order (rather than e.g. putting the whole thing into a set)
                 ;; so that new ones don't jump up in the middle as they're loaded.
                 (filter vcs-github?
                  (concat [user]
                          (:organizations user)
                          (let [org-names (->> user :organizations (cons user) (map :login) set)
                                in-orgs? (comp org-names :login)]
                            (->> repos (map :owner) (remove in-orgs?) (set))))))]
           (when (get-in user [:repos-loading :github])
             [:div.orgs-loading
              [:div.loading-spinner common/spinner]])
           (missing-org-info owner)]])))))

(defn select-vcs-type [vcs-type item]
  (case vcs-type
    "bitbucket" (vcs-bitbucket? item)
    "github"    (vcs-github?    item)))

(defn organization-listing-with-bitbucket [data owner]
  (reify
    om/IDisplayName (display-name [_] "Organization Listing")
    om/IDidMount
    (did-mount [_]
      (utils/tooltip "#collaborators-tooltip-hack" {:placement "right"}))
    om/IRender
    (render [_]
      (let [{:keys [user settings repos tab]} data
            vcs-type (or tab "github")
            github-active? (= "github" vcs-type)
            bitbucket-active? (= "bitbucket" vcs-type)]
        (html
         [:div
          [:div.overview
           [:span.big-number "1"]
           [:div.instruction "Choose an organization that you are a member of."]]
          [:ul.nav.nav-tabs
           [:li {:class (when github-active? "active")}
            [:a {:href "#github"}
             [:i.octicon.octicon-mark-github]
             " GitHub"]]
           [:li {:class (when bitbucket-active? "active")}
            [:a {:href "#bitbucket"}
             [:i.fa.fa-bitbucket]
             " Bitbucket"]]]
          [:div.organizations.card
           (when github-active?
             (missing-org-info owner))
           (when (and bitbucket-active? (-> user :bitbucket_authorized not))
             [:div
             [:p "Bitbucket is not connected to your account yet. To connect it, click the button below:"]
             [:a.btn.btn-primary {:href (bitbucket/auth-url)} "Authorize with Bitbucket"]])
           [:ul.organizations
            (map (fn [org] (organization org settings owner))
                 ;; here we display you, then all of your organizations, then all of the owners of
                 ;; repos that aren't organizations and aren't you. We do it this way because the
                 ;; organizations route is much faster than the repos route. We show them
                 ;; in this order (rather than e.g. putting the whole thing into a set)
                 ;; so that new ones don't jump up in the middle as they're loaded.
                 (filter (partial select-vcs-type vcs-type)
                         (concat [(when (= "github" vcs-type)
                                    (assoc user :vcs_type "github"))]
                                 (:organizations user)
                                 (let [org-names (->> user
                                                      :organizations
                                                      (cons user)
                                                      (map :login)
                                                      set)
                                       in-orgs? (comp org-names :login)]
                                   (reduce (fn [accum {:keys [owner vcs_type]}]
                                             (if (in-orgs? owner)
                                               accum
                                               (conj accum (assoc owner :vcs_type vcs_type))))
                                           #{}
                                           repos)))))]
           (when (get-in user [:repos-loading (keyword vcs-type)])
             [:div.orgs-loading
              [:div.loading-spinner common/spinner]])]])))))

(def repos-explanation
  [:div.add-repos
   [:ul
    [:li
     "Get started by selecting your GitHub "
     (when (feature/enabled? :bitbucket)
       "or Bitbucket ")
     "username or organization."]
    [:li "Choose a repo you want to test and we'll do the rest!"]]])

(defn add-projects-head-actions [data owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.refresh-repos
       [:button.btn.btn-primary.set-user
        {:on-click #(raise! owner [:refreshed-user-orgs {}])} ;; TODO: spinner while working?
        "Reload repositories"]]))))

(defn repo-item [data owner]
  (reify
    om/IDisplayName (display-name [_] "repo-item")
    om/IDidMount
    (did-mount [_]
      (utils/tooltip (str "#view-project-tooltip-" (-> data :repo repo-model/id (string/replace #"[^\w]" "")))))
    om/IRenderState
    (render-state [_ {:keys [building?]}]
      (let [repo (:repo data)
            settings (:settings data)
            login (get-in settings [:add-projects :selected-org :login])
            type (get-in settings [:add-projects :selected-org :type])
            repo-id (repo-model/id repo)
            tooltip-id (str "view-project-tooltip-" (string/replace repo-id #"[^\w]" ""))
            settings (:settings data)
            should-build? (repo-model/should-do-first-follower-build? repo)]
        (html
         (cond (repo-model/can-follow? repo)
               [:li.repo-follow
                [:div.proj-name
                 [:span {:title (str (vcs-url/project-name (:vcs_url repo))
                                     (when (:fork repo) " (forked)"))}
                  (:name repo)]
                 (when (repo-model/likely-osx-repo? repo)
                   [:i.fa.fa-apple])]
                (when building?
                  [:div.building "Starting first build..."])
                (managed-button
                 [:button {:on-click #(do (raise! owner [:followed-repo (assoc @repo
                                                                               :login login
                                                                               :type type)])
                                          (when should-build?
                                            (om/set-state! owner :building? true)))
                           :title (if should-build?
                                    "This project has never been built by CircleCI before. Clicking will cause CircleCI to start building the project."
                                    "This project has been built by CircleCI before. Clicking will cause builds for this project to show up for you in the UI.")
                           :data-spinner true}
                  (if should-build? "Build project" "Watch project")])]

               (:following repo)
               [:li.repo-unfollow
                [:a {:title (str "View " (:name repo) (when (:fork repo) " (forked)") " project")
                     :href (vcs-url/project-path (:vcs_url repo))}
                 " "
                 [:div.proj-name
                  [:span {:title (str (vcs-url/project-name (:vcs_url repo))
                                      (when (:fork repo) " (forked)"))}
                   (:name repo)
                   (when (repo-model/likely-osx-repo? repo)
                     [:i.fa.fa-apple])]

                  (when (:fork repo)
                    [:span.forked (str " (" (vcs-url/org-name (:vcs_url repo)) ")")])]]

                (managed-button
                 [:button {:on-click #(raise! owner [:unfollowed-repo (assoc @repo
                                                                             :login login
                                                                             :type type)])
                           :data-spinner true}
                  [:span "Stop watching project"]])]

               (repo-model/requires-invite? repo)
               [:li.repo-nofollow
                [:div.proj-name
                 [:span {:title (str (vcs-url/project-name (:vcs_url repo))
                                     (when (:fork repo) " (forked)"))}
                  (:name repo)]
                 (when (:fork repo)
                   [:span.forked [:i.octicon.octicon-repo-forked] (str " " (vcs-url/org-name (:vcs_url repo)) "")])]
                [:div.notice {:title "You must be an admin to add a project on CircleCI"}
                 [:i.material-icons.lock "lock"]
                 "Contact repo admin"]]))))))

(defrender repo-filter [settings owner]
  (let [repo-filter-string (get-in settings [:add-projects :repo-filter-string])]
    (html
     [:div.repo-filter
      [:input.unobtrusive-search
       {:placeholder "Filter repos..."
        :type "search"
        :value repo-filter-string
        :on-change #(utils/edit-input owner [:settings :add-projects :repo-filter-string] %)}]
      [:div.checkbox.pull-right.fork-filter
       [:label
        [:input {:type "checkbox"
                 :checked (-> settings :add-projects :show-forks)
                 :name "Show forks"
                 :on-change #(utils/toggle-input owner [:settings :add-projects :show-forks] %)}]
        "Show Forks"]]])))

(defn empty-repo-list [loading-repos? repo-filter-string selected-org-login]
  (if loading-repos?
    [:div.loading-spinner common/spinner]
    [:div.add-repos
     (if repo-filter-string
       (str "No matching repos for organization " selected-org-login)
       (str "No repos found for organization " selected-org-login))]))

(defn select-plan-button [{:keys [selected-org-login]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:a.btn.btn-primary.plan {:href (routes/v1-org-settings-path {:org selected-org-login
                                                                      :_fragment "osx-pricing"})
                                  :on-click #(analytics/track {:event-type :select-plan-clicked
                                                               :owner owner
                                                               :properties {:org selected-org-login
                                                                            :plan-type (pm/osx-plan-type)}})}

         "Select Plan"]))))

(defn free-trial-button [{:keys [selected-org-login]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        (managed-button
          (let [plan-type :osx
                template "osx-trial"]
            [:a.btn.trial {:on-click #(do
                                        (raise! owner [:activate-plan-trial {plan-type {:template template}}])
                                        (analytics/track {:event-type :start-trial-clicked
                                                          :owner owner
                                                          :properties {:org selected-org-login
                                                                       :plan-type plan-type
                                                                       :template template}}))
                           :data-spinner true}
             "Start 2 Week Trial"]))))))

(defn no-plan-empty-state [{:keys [selected-org-login]} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (analytics/track {:event-type :no-plan-banner-impression
                        :owner owner
                        :properties {:org selected-org-login
                                     :plan-type (pm/osx-plan-type)}}))
    om/IRender
    (render [_]
      (html
        [:div.no-plan-empty-state
         [:i.fa.fa-apple.apple-logo]
         [:div.title
          [:span.bold selected-org-login] " has no " [:span.bold "OS X plan"] " on CircleCI."]
         [:div.info
          "Select a plan to build your OS X projects now."]
         [:div.buttons
          (om/build select-plan-button {:selected-org-login selected-org-login})
          (om/build free-trial-button {:selected-org-login selected-org-login})]]))))

(defmulti repo-list (fn [{:keys [type]}] type))

(defmethod repo-list :linux [{:keys [repos loading-repos? repo-filter-string selected-org-login selected-plan settings]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        (if (empty? repos)
          (empty-repo-list loading-repos? repo-filter-string selected-org-login)
          [:ul.proj-list.list-unstyled
           (for [repo repos]
             (om/build repo-item {:repo repo :settings settings}))])))))

(defmethod repo-list :osx [{:keys [repos loading-repos? repo-filter-string selected-org-login selected-plan settings]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        (if (empty? repos)
          (empty-repo-list loading-repos? repo-filter-string selected-org-login)
          [:ul.proj-list.list-unstyled
           (if-not (pm/osx? selected-plan)
             (om/build no-plan-empty-state {:selected-org-login selected-org-login})
             (for [repo repos]
               (om/build repo-item {:repo repo :settings settings})))])))))

(defn repo-lists [{:keys [user repos selected-org osx-enabled? selected-plan settings] :as data} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-tab :linux})

    om/IRenderState
    (render-state [_ {:keys [selected-tab]}]
      (let [selected-org-login (:login selected-org)
            loading-repos? (get-in user [:repos-loading (keyword (:vcs-type selected-org))])
            repo-filter-string (get-in settings [:add-projects :repo-filter-string])
            show-forks (true? (get-in settings [:add-projects :show-forks]))]
        (html
          [:div.proj-wrapper
           [:div.tabbed-wrapper
            (when osx-enabled?
              [:ul.nav.nav-tabs
               [:li {:class (when (= selected-tab :linux) "active")}
                [:a {:on-click #(om/set-state! owner [:selected-tab] :linux)}
                 [:i.fa.fa-linux.fa-lg] "Linux"]]
               [:li {:class (when (= selected-tab :osx) "active")}
                [:a {:on-click #(om/set-state! owner [:selected-tab] :osx)}
                 [:i.fa.fa-apple.fa-lg] " OS X"]]])
            (if selected-org-login
              (let [;; we display a repo if it belongs to this org, matches the filter string,
                    ;; and matches the fork settings.
                    display? (fn [repo]
                               (and
                                 (or show-forks (not (:fork repo)))
                                 (select-vcs-type (or (:vcs-type selected-org)
                                                      "github") repo)
                                 (= (:username repo) selected-org-login)
                                 (gstring/caseInsensitiveContains (:name repo) repo-filter-string)))
                    filtered-repos (->> repos
                                        (filter display?)
                                        (sort-by :pushed_at)
                                        (reverse))
                    osx-repos (->> filtered-repos (filter repo-model/likely-osx-repo?))
                    linux-repos (->> filtered-repos (remove repo-model/likely-osx-repo?))]
                [:div
                 [:div
                  (om/build repo-filter settings)]
                 [:div
                  (condp = selected-tab
                    :linux
                    (om/build repo-list {:repos (if (and osx-enabled? (pm/osx? selected-plan)) ; Allows mistaken OS X repos to still be built.
                                                  linux-repos
                                                  filtered-repos)
                                         :loading-repos? loading-repos?
                                         :repo-filter-string repo-filter-string
                                         :selected-org-login selected-org-login
                                         :selected-plan selected-plan
                                         :type selected-tab
                                         :settings settings})

                    :osx
                    (om/build repo-list {:repos osx-repos
                                         :loading-repos? loading-repos?
                                         :repo-filter-string repo-filter-string
                                         :selected-org-login selected-org-login
                                         :selected-plan selected-plan
                                         :type selected-tab
                                         :settings settings}))]])
              repos-explanation)]])))))

(defn inaccessible-follows
  "Any repo we follow where the org isn't in our set of orgs is either: an org
  we have been removed from, or an org that turned on 3rd party app restrictions
  and didn't enable CircleCI"
  [user-data followed]
  (let [org-set (set (map :login (:organizations user-data)))
        org-set (conj org-set (:login user-data))]
    (filter #(not (contains? org-set (:username %))) followed)))

(defn inaccessible-repo-item [data owner]
  (reify
    om/IDisplayName (display-name [_] "repo-item")
    om/IRenderState
    (render-state [_ {:keys [building?]}]
      (let [repo (:repo data)
            settings (:settings data)
            login (get-in repo [:username])]
        (html
         [:li.repo-unfollow
          [:div.proj-name
           [:span {:title (str (:reponame repo) (when (:fork repo) " (forked)"))}
            (:reponame repo)]
           (when (:fork repo)
             [:span.forked (str " (" (vcs-url/org-name (:vcs_url repo)) ")")])]
          (managed-button
           [:button {:on-click #(raise! owner [:unfollowed-repo (assoc @repo
                                                                  :login login
                                                                  :type type)])
                     :data-spinner true}
            [:span "Stop watching project"]])])))))

(defn inaccessible-org-item [data owner]
  (reify
    om/IDisplayName (display-name [_] "org-item")
    om/IRenderState
    (render-state [_ {:keys [building?]}]
      (let [repos (:repos data)
            settings (:settings data)
            org-name (:org-name data)
            visible? (get-in settings [:add-projects :inaccessible-orgs org-name :visible?])]
        (html
         [:div
          [:div.repo-filter
           [:div.orgname {:on-click #(raise! owner [:inaccessible-org-toggled {:org-name org-name :value (not visible?)}])}
            (if visible?
              [:i.fa.fa-chevron-up]
              [:i.fa.fa-chevron-down])
            [:span {:title org-name} org-name]]]
          (when visible?
            [:ul.proj-list.list-unstyled
             (map (fn [repo] (om/build inaccessible-repo-item {:repo repo :settings settings}))
                  repos)])])))))

(defn inaccessible-orgs-notice [follows settings]
  (let [inaccessible-orgs (set (map :username follows))
        follows-by-orgs (group-by :username follows)]
    [:div.inaccessible-notice.card
     [:h2 "Warning: Access Problems"]
     [:p.missing-org-info
      "You are following repositories owned by GitHub organizations to which you don't currently have access. If an admin for the org recently enabled the new GitHub Third Party Application Access Restrictions for these organizations, you may need to enable CircleCI access for the orgs at "
      [:a.gh_app_permissions {:href (gh-utils/third-party-app-restrictions-url) :target "_blank"}
       "GitHub's application permissions"]
      "."]
     [:div.inaccessible-org-wrapper
      (map (fn [org-follows] (om/build inaccessible-org-item
                                       {:org-name (:username (first org-follows)) :repos org-follows :settings settings}))
           (vals follows-by-orgs))]]))

(defrender add-projects [data owner]
  (let [user (:current-user data)
        repos (:repos user)
        settings (:settings data)
        {{tab :tab} :navigation-data} data
        selected-org (get-in settings [:add-projects :selected-org])
        followed-inaccessible (inaccessible-follows user
                                                    (get-in data state/projects-path))]
    (html
     [:div#add-projects
      (when (seq (user-model/missing-scopes user))
        (missing-scopes-notice (:github_oauth_scopes user) (user-model/missing-scopes user)))
      (when (seq followed-inaccessible)
        (inaccessible-orgs-notice followed-inaccessible settings))
      [:h2 "CircleCI helps you ship better code, faster. Let's add some projects on CircleCI."]
      [:p "To kick things off, you'll need to pick some projects to build:"]
      [:hr]
      [:div.org-repo-container
       [:div.app-aside.org-listing
        (om/build (if (feature/enabled? :bitbucket)
                    organization-listing-with-bitbucket
                    organization-listing)
                  {:user user
                   :settings settings
                   :repos repos
                   :tab tab})]
       [:div#project-listing.project-listing
        [:div.overview
         [:span.big-number "2"]
         [:div.instruction "Choose a repo below, and we will watch the repository for activity like commits and pull requests. We'll kick off the first build immediately, and new builds will be initiated each time someone pushes commits."]]
         (om/build repo-lists {:user user
                               :repos repos
                               :selected-org selected-org
                               :osx-enabled? (get-in data state/org-osx-enabled-path)
                               :selected-plan (get-in data state/org-plan-path)
                               :settings settings})]]])))
