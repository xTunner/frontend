(ns frontend.components.add-projects
  (:require [clojure.string :as string]
            [frontend.async :refer [raise! navigate!]]
            [frontend.components.common :as common]
            [frontend.components.forms :refer [managed-button]]
            [frontend.components.pieces.org-picker :as org-picker]
            [frontend.components.pieces.tabs :as tabs]
            [frontend.models.plan :as pm]
            [frontend.models.repo :as repo-model]
            [frontend.models.user :as user-model]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils]
            [frontend.utils.bitbucket :as bitbucket]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs :as vcs-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html]]))

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

(defn select-vcs-type [vcs-type item]
  (case vcs-type
    "bitbucket" (vcs-bitbucket? item)
    "github"    (vcs-github?    item)))

(defn repos-explanation [user]
  [:div.add-repos
   [:ul
    [:li
     "Get started by selecting your GitHub "
     (when (vcs-utils/bitbucket-enabled? user)
       "or Bitbucket ")
     "username or organization."]
    [:li "Choose a repo you want to test and we'll do the rest!"]]])

(defn add-projects-head-actions [data owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:button.btn.btn-primary
        {:on-click #(raise! owner [:refreshed-user-orgs {}])} ;; TODO: spinner while working?
        "Reload Organizations"]))))

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
                 [:button {:on-click #(do
                                        (raise! owner [:followed-repo (assoc @repo
                                                                             :login login
                                                                             :type type)])
                                        (when should-build?
                                          (om/set-state! owner :building? true)))
                           :title (if should-build?
                                    "This project has never been built by CircleCI before. Clicking will cause CircleCI to start building the project."
                                    "This project has been built by CircleCI before. Clicking will cause builds for this project to show up for you in the UI.")
                           :data-spinner true}
                  (if should-build? "Build project" "Follow project")])]

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
                  [:span "Unfollow project"]])]

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

(defn select-plan-button [{{:keys [login vcs-type]} :selected-org} owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:a.btn.btn-primary.plan {:href (routes/v1-org-settings-path {:org login
                                                                     :vcs_type vcs-type
                                                                     :_fragment "osx-pricing"})
                                 :on-click #((om/get-shared owner :track-event)
                                             {:event-type :select-plan-clicked
                                              :properties {:org login
                                                           :vcs-type vcs-type
                                                           :plan-type pm/osx-plan-type}})}
        "Select Plan"]))))

(defn free-trial-button [{{:keys [login vcs-type]} :selected-org} owner]
  (reify
    om/IRender
    (render [_]
      (html
        (managed-button
          (let [plan-type :osx
                template "osx-trial"]
            [:a.btn.trial {:on-click #(do
                                        (raise! owner [:activate-plan-trial {:plan-type plan-type
                                                                             :template template
                                                                             :org {:name login
                                                                                   :vcs_type vcs-type}}]))
                           :data-spinner true}
             "Start 2 Week Trial"]))))))

(defn no-plan-empty-state [{{:keys [login vcs-type] :as selected-org} :selected-org} owner]
  (reify
    om/IDidMount
    (did-mount [_]
      ((om/get-shared owner :track-event) {:event-type :no-plan-banner-impression
                                           :properties {:org login
                                                        :vcs-type vcs-type
                                                        :plan-type pm/osx-plan-type}}))
    om/IRender
    (render [_]
      (html
        [:div.no-plan-empty-state
         [:i.fa.fa-apple.apple-logo]
         [:div.title
          [:span.bold login] " has no " [:span.bold "OS X plan"] " on CircleCI."]
         [:div.info
          "Select a plan to build your OS X projects now."]
         [:div.buttons
          (om/build select-plan-button {:selected-org selected-org})
          (om/build free-trial-button {:selected-org selected-org})]]))))

(defmulti repo-list (fn [{:keys [type]}] type))

(defmethod repo-list :linux [{:keys [repos loading-repos? repo-filter-string selected-org selected-plan settings]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        (if (empty? repos)
          (empty-repo-list loading-repos? repo-filter-string (:login selected-org))
          [:ul.proj-list.list-unstyled
           (for [repo repos]
             (om/build repo-item {:repo repo :settings settings}))])))))

(defmethod repo-list :osx [{:keys [repos loading-repos? repo-filter-string selected-org selected-plan settings]} owner]
  (reify
    om/IRender
    (render [_]
      (html
       (if (empty? repos)
         (empty-repo-list loading-repos? repo-filter-string (:login selected-org))
         [:ul.proj-list.list-unstyled
          (if-not (pm/osx? selected-plan)
            (om/build no-plan-empty-state {:selected-org selected-org})
            (for [repo repos]
              (om/build repo-item {:repo repo :settings settings})))])))))

(defn repo-lists [{:keys [user repos selected-org selected-plan settings] :as data} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-tab-name :linux})

    om/IRenderState
    (render-state [_ {:keys [selected-tab-name]}]
      (let [selected-org-login (:login selected-org)
            loading-repos? (get-in user [:repos-loading (keyword (:vcs_type selected-org))])
            repo-filter-string (get-in settings [:add-projects :repo-filter-string])
            show-forks (true? (get-in settings [:add-projects :show-forks]))]
        (html
         [:div.proj-wrapper
          (if-not selected-org-login
            (repos-explanation user)
            (list
             (om/build tabs/tab-row {:tabs [{:name :linux
                                             :icon (html [:i.fa.fa-linux.fa-lg])
                                             :label "Linux"}
                                            {:name :osx
                                             :icon (html [:i.fa.fa-apple.fa-lg])
                                             :label "OS X"}]
                                     :selected-tab-name selected-tab-name
                                     :on-tab-click #(om/set-state! owner [:selected-tab-name] %)})

             (let [;; we display a repo if it belongs to this org, matches the filter string,
                   ;; and matches the fork settings.
                   display? (fn [repo]
                              (and
                               (or show-forks (not (:fork repo)))
                               (select-vcs-type (or (:vcs_type selected-org)
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
                 (condp = selected-tab-name
                   :linux
                   (om/build repo-list {:repos (if (pm/osx? selected-plan) ; Allows mistaken OS X repos to still be built.
                                                 linux-repos
                                                 filtered-repos)
                                        :loading-repos? loading-repos?
                                        :repo-filter-string repo-filter-string
                                        :selected-org selected-org
                                        :selected-plan selected-plan
                                        :type selected-tab-name
                                        :settings settings})

                   :osx
                   (om/build repo-list {:repos osx-repos
                                        :loading-repos? loading-repos?
                                        :repo-filter-string repo-filter-string
                                        :selected-org selected-org
                                        :selected-plan selected-plan
                                        :type selected-tab-name
                                        :settings settings}))]])))])))))

(defn inaccessible-follows
  "Any repo we follow where the org isn't in our set of orgs is either: an org
  we have been removed from, or an org that turned on 3rd party app restrictions
  and didn't enable CircleCI"
  [user-data followed]
  (let [org-set (->> user-data
                     :organizations
                     (filter :org)
                     (map :login)
                     set)
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
            [:span "Unfollow project"]])])))))

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
      [:a {:href (gh-utils/third-party-app-restrictions-url) :target "_blank"}
       "GitHub's application permissions"]
      "."]
     [:div.inaccessible-org-wrapper
      (map (fn [org-follows] (om/build inaccessible-org-item
                                       {:org-name (:username (first org-follows)) :repos org-follows :settings settings}))
           (vals follows-by-orgs))]]))

(defn- missing-org-info
  "A message explaining how to enable organizations which have disallowed CircleCI on GitHub."
  [owner]
  (html
   [:p
    "Are you missing an organization? You or an admin may need to enable CircleCI for your organization in "
    [:a {:href (gh-utils/third-party-app-restrictions-url) :target "_blank"}
     "GitHub's application permissions"]
    ". "
    [:a {:on-click #(raise! owner [:refreshed-user-orgs {}]) ;; TODO: spinner while working?
         :class "active"}
     "Refresh this list"]
    " after you have updated permissions."]))

(defn- org-picker-without-bitbucket [{:keys [orgs user selected-org]} owner]
  (reify
    om/IDisplayName (display-name [_] "Organization Listing")
    om/IRender
    (render [_]
      (html
       [:div
        [:h4 "Your accounts"]
        (om/build org-picker/picker {:orgs (filter vcs-github? orgs)
                                     :selected-org selected-org
                                     :on-org-click #(raise! owner [:selected-add-projects-org %])})
        (when (get-in user [:repos-loading :github])
          [:div.orgs-loading
           [:div.loading-spinner common/spinner]])
        (missing-org-info owner)]))))

(defn- org-picker-with-bitbucket [{:keys [orgs user selected-org tab]} owner]
  (reify
    om/IDisplayName (display-name [_] "Organization Listing")
    om/IRender
    (render [_]
      (let [github-authorized? (user-model/github-authorized? user)
            bitbucket-authorized? (user-model/bitbucket-authorized? user)
            selected-vcs-type (cond
                                tab tab
                                github-authorized? "github"
                                :else "bitbucket")
            github-active? (= "github" selected-vcs-type)
            bitbucket-active? (= "bitbucket" selected-vcs-type)]
        (html
         [:div
          (om/build tabs/tab-row {:tabs [{:name "github"
                                          :icon (html [:i.octicon.octicon-mark-github])
                                          :label "GitHub"}
                                         {:name "bitbucket"
                                          :icon (html [:i.fa.fa-bitbucket])
                                          :label "Bitbucket"}]
                                  :selected-tab-name selected-vcs-type
                                  :on-tab-click #(navigate! owner (routes/v1-add-projects-path {:_fragment %}))})
          [:div.organizations.card
           (when github-active?
             (if github-authorized?
               (missing-org-info owner)
               [:div
                [:p "GitHub is not connected to your account yet. To connect it, click the button below:"]
                [:a.btn.btn-primary {:href (gh-utils/auth-url)
                                     :on-click #((om/get-shared owner :track-event) {:event-type :authorize-vcs-clicked
                                                                                     :properties {:vcs-type selected-vcs-type}})}
                 "Authorize with GitHub"]]))
           (when (and bitbucket-active?
                      (not bitbucket-authorized?))
             [:div
              [:p "Bitbucket is not connected to your account yet. To connect it, click the button below:"]
              [:a.btn.btn-primary {:href (bitbucket/auth-url)
                                   :on-click #((om/get-shared owner :track-event) {:event-type :authorize-vcs-clicked
                                                                                   :properties {:vcs-type selected-vcs-type}})}
               "Authorize with Bitbucket"]])
           (om/build org-picker/picker {:orgs (filter (partial select-vcs-type selected-vcs-type) orgs)
                                        :selected-org selected-org
                                        :on-org-click #(raise! owner [:selected-add-projects-org %])})
           (when (get-in user [:repos-loading (keyword selected-vcs-type)])
             [:div.orgs-loading
              [:div.loading-spinner common/spinner]])]])))))

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
        ;; We display you, then all of your organizations, then all of the owners of
        ;; repos that aren't organizations and aren't you. We do it this way because the
        ;; organizations route is much faster than the repos route. We show them
        ;; in this order (rather than e.g. putting the whole thing into a set)
        ;; so that new ones don't jump up in the middle as they're loaded.
        (let [user-org-keys (->> user
                                 :organizations
                                 (map (juxt :vcs_type :login))
                                 set)
              user-org? (comp user-org-keys (juxt :vcs_type :login))
              orgs (concat (sort-by :org (:organizations user))
                           (->> repos
                                (map (fn [{:keys [owner vcs_type]}] (assoc owner :vcs_type vcs_type)))
                                (remove user-org?)
                                distinct))]
          [:div
           [:div.overview
            [:span.big-number "1"]
            [:div.instruction "Choose an organization that you are a member of."]]
           (if (vcs-utils/bitbucket-enabled? user)
             (om/build org-picker-with-bitbucket {:orgs orgs
                                                  :selected-org selected-org
                                                  :user user
                                                  :tab tab})
             (om/build org-picker-without-bitbucket {:orgs orgs
                                                     :selected-org selected-org
                                                     :user user}))])]
       [:div#project-listing.project-listing
        [:div.overview
         [:span.big-number "2"]
         [:div.instruction
          [:p "Choose a repo to add to CircleCI. We'll start a new build for you each time someone pushes a new commit."]
          [:p "You can also follow a repo that's already been added to CircleCI. You'll see your followed projects in "
           [:a {:href (routes/v1-dashboard-path {})} "Builds"]
           " and "
           [:a {:href (routes/v1-insights)} "Insights"]
           "."]]]
        (om/build repo-lists {:user user
                              :repos repos
                              :selected-org selected-org
                              :selected-plan (get-in data state/org-plan-path)
                              :settings settings})]]])))
