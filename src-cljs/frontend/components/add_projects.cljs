(ns frontend.components.add-projects
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.datetime :as datetime]
            [frontend.models.user :as user-model]
            [frontend.models.repo :as repo-model]
            [frontend.components.common :as common]
            [frontend.components.forms :refer [managed-button]]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [inspect]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [html defrender]]))

(defn missing-scopes-notice [current-scopes missing-scopes]
  [:div
   [:div.alert.alert-error
    "We don't have all of the GitHub OAuth scopes we need to run your tests."
    ;; TODO translate CI.github
    [:a {:href (js/CI.github.authUrl (clj->js (concat missing-scopes current-scopes)))}
     (gstring/format "Click to grant Circle the %s %s."
                     (string/join "and " missing-scopes)
                     (if (< 1 (count missing-scopes)) "scope" "scopes"))]]])

(defn organization [org settings owner]
  (let [login (:login org)
        type (if (:org org) :org :user)]
    [:li.organization {:on-click #(raise! owner [:selected-add-projects-org {:login login :type type}])
                        :class (when (= {:login login :type type} (get-in settings [:add-projects :selected-org])) "active")}
     [:img.avatar {:src (gh-utils/make-avatar-url org :size 50)
            :height 50}]
     [:div.orgname login]
     [:a.visit-org {:href (str (gh-utils/http-endpoint) "/" login)
                    :target "_blank"}
      [:i.fa.fa-github-alt ""]]]))

(defn missing-org-info
  "A message explaining how to enable organizations which have disallowed CircleCI on GitHub."
  [owner]
  [:p.missing-org-info
   "Missing an organization? You or an admin may need to enable CircleCI for your organization in "
   [:a.gh_app_permissions {:href (gh-utils/third-party-app-restrictions-url) :target "_blank"}
    "GitHub's application permissions."]
   " Then come back and "
   [:a {:on-click #(raise! owner [:refreshed-user-orgs {}]) ;; TODO: spinner while working?
                      :class "active"}
    "refresh these listings"]
   "."])

(defn organization-listing [data owner]
  (reify
    om/IDisplayName (display-name [_] "Organization Listing")
    om/IDidMount
    (did-mount [_]
      (utils/tooltip "#collaborators-tooltip-hack" {:placement "right"}))
    om/IRender
    (render [_]
      (let [{:keys [user settings]} data
            show-fork-accounts? (get-in settings [:add-projects :show-fork-accounts])]
        (html
          [:div
           [:div.overview
            [:span.big-number "1"]
            [:div.instruction "Choose a GitHub account that you are a member of or have access to."]]
           [:div.organizations
            [:h4 "Your accounts"]
            [:ul.organizations
             (map (fn [org] (organization org settings owner))
                  (:organizations user))
             (map (fn [org] (organization org settings owner))
                  (filter (fn [org] (= (:login user) (:login org)))
                          (:collaborators user)))]
            (missing-org-info owner)]
           [:div.organizations
            [:h4
             [:a
              {:on-click #(raise! owner [:toggled-input {:path [:settings :add-projects :show-fork-accounts]}])}
              "Users & organizations who have made pull requests to your repos "
              (if show-fork-accounts?
                [:i.fa.fa-chevron-down ""]
                [:i.fa.fa-chevron-up ""])]]
            (if show-fork-accounts?
              [:ul.organizations
               (map (fn [org] (organization org settings owner))
                    (remove (fn [org] (= (:login user) (:login org)))
                            (:collaborators user)))])]])))))

(def repos-explanation
  [:div.add-repos
   [:ul
    [:li
     "Get started by selecting your GitHub username or organization above."]
    [:li "Choose a repo you want to test and we'll do the rest!"]]])

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
                  (:name repo)]]
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
                [:div.proj-name
                 [:span {:title (str (vcs-url/project-name (:vcs_url repo))
                                     (when (:fork repo) " (forked)"))}
                  (:name repo)]
                 [:a {:id tooltip-id
                      :title (str "View " (:name repo) (when (:fork repo) " (forked)") " project")
                      :href (vcs-url/project-path (:vcs_url repo))}
                  " "
                  [:i.fa.fa-external-link]]
                 (when (:fork repo)
                   [:span.forked (str " (" (vcs-url/org-name (:vcs_url repo)) ")")])]
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
                   [:span.forked (str " (" (vcs-url/org-name (:vcs_url repo)) ")")])]
                [:button {:on-click #(utils/open-modal "#inviteForm-addprojects")}
                 [:i.fa.fa-lock]
                 "Contact repo admin"]]))))))

(def invite-modal
  [:div#inviteForm-addprojects.fade.hide.modal
   {:tabIndex "-1",
    :role "dialog",
    :aria-labelledby "inviteFormLabel",
    :aria-hidden "true"}
   [:div.modal-header
    [:button.close
     {:type "button", :data-dismiss "modal", :aria-hidden "true"}
     "×"]
    [:h3#inviteFormLabel "This requires an Administrator"]]
   [:div.modal-body
    [:p
     "For security purposes only a project's Github administrator may setup Circle. Invite this project's admin(s) by sending them the link below and asking them to setup the project in Circle. You may also ask them to make you a Github administrator."]
    [:p [:input {:value "https://circleci.com/?join=dont-test-alone", :type "text"}]]]
   [:div.modal-footer
    [:button.btn.btn-primary
     {:data-dismiss "modal", :aria-hidden "true"}
     "Got it"]]])

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
                 :name "Show forks"
                 :on-change #(utils/toggle-input owner [:settings :add-projects :show-forks] %)}]
        "Show forks"]]])))

(defrender main [data owner]
  (let [user (:current-user data)
        settings (:settings data)
        repos (:repos data)
        repo-filter-string (get-in settings [:add-projects :repo-filter-string])
        show-forks (true? (get-in settings [:add-projects :show-forks]))]
    (html
     [:div.proj-wrapper
      (if-not (get-in settings [:add-projects :selected-org :login])
        repos-explanation
        (cond
         (nil? repos) [:div.loading-spinner common/spinner]
         (not (seq repos)) [:div
                            (om/build repo-filter settings)
                            [:ul.proj-list.list-unstyled
                             [:li (str "No repos found for organization " (:selected-org data))]]]
         :else [:div
                (om/build repo-filter settings)
                [:ul.proj-list.list-unstyled
                 (let [filtered-repos (sort-by :updated_at (filter (fn [repo]
                                                                    (and
                                                                     (or show-forks (not (:fork repo)))
                                                                     (gstring/caseInsensitiveContains
                                                                       (:name repo)
                                                                       repo-filter-string)))
                                                                  repos))]
                   (map (fn [repo] (om/build repo-item {:repo repo
                                                        :settings settings}))
                        filtered-repos))]]))
      invite-modal])))

(defn inaccessible-follows
  "Any repo we follow where the org isn't in our set of orgs is either: an org
  we have been removed from, or an org that turned on 3rd party app restrictions
  and didn't enable CircleCI"
  [user-data followed]
  (let [org-set (set (map :login (:organizations user-data)))
        org-set (conj org-set (:login user-data))]
    (comment
      (filter identity
            (map-indexed (fn [index item]
                           (when (not (contains? org-set (:username item)))
                             (assoc item :index index))))))
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
            [:span {:title org-name} (str org-name " ")]
            (if visible? [:i.fa.fa-chevron-up] [:i.fa.fa-chevron-down])]]
          (when visible?
            [:ul.proj-list.list-unstyled
             (map (fn [repo] (om/build inaccessible-repo-item {:repo repo :settings settings}))
                  repos)])])))))

(defn inaccessible-orgs-notice [follows settings]
  (let [inaccessible-orgs (set (map :username follows))
        follows-by-orgs (group-by :username follows)]
    [:div.inaccessible-notice
     [:h2 "Warning: Access Problems"]
     [:p.missing-org-info
      "You are following repositories owned by GitHub organizations to which you don't currently have access. If an admin for the org recently enabled the new GitHub Third Party Application Access Restrictions for these organizations, you may need to enable CircleCI access for the orgs at "
      [:a.gh_app_permissions {:href (gh-utils/third-party-app-restrictions-url) :target "_blank"}
       "GitHub's application permissions."]]
     [:div.inaccessible-org-wrapper
      (map (fn [org-follows] (om/build inaccessible-org-item
                                      {:org-name (:username (first org-follows)) :repos org-follows :settings settings}))
           (vals follows-by-orgs))]]))

(defrender add-projects [data owner]
  (let [user (:current-user data)
        settings (:settings data)
        selected-org (get-in settings [:add-projects :selected-org :login])
        repo-key (gstring/format "%s.%s"
                                 selected-org
                                 (get-in settings [:add-projects :selected-org :type]))
        repos (get-in user [:repos repo-key])
        followed-inaccessible (inaccessible-follows user
                                                    (get-in data state/projects-path))]
    (html
     [:div#add-projects
      [:header.main-head
       [:div.head-user
        [:h1 "Add Projects"]]]
      [:div#follow-contents
       [:div.follow-wrapper
        (when (seq (user-model/missing-scopes user))
          (missing-scopes-notice (:github_oauth_scopes user) (user-model/missing-scopes user)))
        (when (seq followed-inaccessible)
          (inaccessible-orgs-notice followed-inaccessible settings))
        [:h2 "Welcome!"]
        [:h3 "You're about to set up a new project in CircleCI."]
        [:p "CircleCI helps you ship better code, faster. To kick things off, you'll need to pick some projects to build:"]
        [:hr]
        [:div.org-listing
         (om/build organization-listing {:user user
                                         :settings settings})]
        [:hr]
        [:div#project-listing.project-listing
         [:div.overview
          [:span.big-number "2"]
          [:div.instruction "Choose a repo, and we'll watch the repository for activity in GitHub such as pushes and pull requests. We'll kick off the first build immediately, and a new build will be initiated each time someone pushes commits."]]
         (om/build main {:user user
                         :repos repos
                         :selected-org selected-org
                         :settings settings})]]]])))

