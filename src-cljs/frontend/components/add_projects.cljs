(ns frontend.components.add-projects
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [raise!]]
            [frontend.datetime :as datetime]
            [frontend.models.user :as user-model]
            [frontend.models.repo :as repo-model]
            [frontend.components.common :as common]
            [frontend.components.forms :refer [managed-button]]
            [frontend.utils :as utils :refer-macros [inspect]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [clojure.string :as string]
            [goog.string :as gstring]
            [goog.string.format])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [html]]))

(defn missing-scopes-notice [current-scopes missing-scopes]
  [:div
   [:div.alert.alert-error
    "We don't have all of the GitHub OAuth scopes we need to run your tests."
    ;; TODO translate CI.github
    [:a {:href (js/CI.github.authUrl (clj->js (concat missing-scopes current-scopes)))}
     (gstring/format "Click to grant Circle the %s %s."
                     (string/join "and " missing-scopes)
                     (if (< 1 (count missing-scopes)) "scope" "scopes"))]]])

(defn side-item [org settings owner]
  (let [login (:login org)
        type (if (:org org) :org :user)]
    [:li.side-item {:class (when (= {:login login :type type} (get-in settings [:add-projects :selected-org])) "active")}
     [:a {:on-click #(raise! owner [:selected-add-projects-org {:login login :type type}])}
      [:img {:src (gh-utils/make-avatar-url org :size 25)
             :height 25}]
      [:div.orgname {:on-click #(raise! owner [:selected-add-projects-org {:login login :type type}])}
       login]]]))

(defn org-sidebar [data owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (utils/tooltip "#collaborators-tooltip-hack" {:placement "right"}))
    om/IRender
    (render [_]
      (let [user (:user data)
            settings (:settings data)]
        (html [:ul.side-list
               [:li.add-orgs "Your Organizations"]
               (map (fn [org] (side-item org settings owner))
                    (:organizations user))
               [:li.add-you "Your Projects"]
               (map (fn [org] (side-item org settings owner))
                    (filter (fn [org] (= (:login user) (:login org)))
                            (:collaborators user)))
               [:li.add-collabs
                [:span#collaborators-tooltip-hack {:title "For all repos & forks"}
                 "Your Collaborators"]]
               (map (fn [org] (side-item org settings owner))
                    (remove (fn [org] (= (:login user) (:login org)))
                            (:collaborators user)))])))))

(def repos-explanation
  [:div.add-repos
   [:h3 "Welcome to Circle"]
   [:ul
    [:li
     "Get started by selecting your GitHub username or organization on the left."]
    [:li "Choose a repo you want to test and we'll do the rest!"]]])

(defn repo-item [data owner]
  (reify
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
               [:li.repo-follow {:class (when should-build? "repo-1stfollow")}
                [:div.proj-name
                 [:span {:title (str (vcs-url/project-name (:vcs_url repo))
                                     (when (:fork repo) " (forked)"))}
                  (:name repo)]
                 (when (:fork repo)
                   [:span.forked (str " (" (vcs-url/org-name (:vcs_url repo)) ")")])]
                (when building?
                  [:div.building "Starting first build..."])
                (managed-button
                 [:button {:on-click #(do (raise! owner [:followed-repo (assoc @repo
                                                                               :login login
                                                                               :type type)])
                                          (when should-build?
                                            (om/set-state! owner :building? true)))
                           :data-spinner true}
                  [:span "Follow"]])]

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
                  [:span "Unfollow"]])]

               (repo-model/requires-invite? repo)
               [:li.repo-nofollow
                [:div.proj-name
                 [:span {:title (str (vcs-url/project-name (:vcs_url repo))
                                     (when (:fork repo) " (forked)"))}
                  (:name repo)]
                 (when (:fork repo)
                   [:span.forked (str " (" (vcs-url/org-name (:vcs_url repo)) ")")])]
                [:i.fa.fa-lock]
                [:button {:on-click #(utils/open-modal "#inviteForm-addprojects")}
                 [:span "Follow"]]]))))))

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

(defn repo-filter [settings owner]
  (reify
    om/IRender
    (render [_]
      (let [repo-filter-string (get-in settings [:add-projects :repo-filter-string])]
        (html
         [:div.repo-filter
          [:i.fa.fa-search]
          [:input.unobtrusive-search.input-large
           {:placeholder "Filter repos..."
            :type "search"
            :value repo-filter-string
            :on-change #(utils/edit-input owner [:settings :add-projects :repo-filter-string] %)}]])))))

(defn main [data owner]
  (reify
    om/IRender
    (render [_]
      (let [user (:current-user data)
            settings (:settings data)
            repos (:repos data)
            repo-filter-string (get-in settings [:add-projects :repo-filter-string])]
        (html
         [:div.proj-wrapper
          (if-not (get-in settings [:add-projects :selected-org :login])
            repos-explanation
            (cond
              (nil? repos)
                [:div.loading-spinner common/spinner]
              (not (seq repos))
                [:ul.proj-list
                  [:li (str "No repos found for organization " (:selected-org data))]]
              :else
                [:ul.proj-list
                 (let [filtered-repos (filter (fn [repo]
                                                (-> repo
                                                    :name
                                                    (.toLowerCase)
                                                    (.indexOf repo-filter-string)
                                                    (not= -1)))
                                              repos)]
                   (map (fn [repo] (om/build repo-item {:repo repo
                                                        :settings settings}))
                        filtered-repos))]))
          invite-modal])))))

(defn add-projects [data owner]
  (reify
    om/IRender
    (render [_]
      (let [user (:current-user data)
            settings (:settings data)
            selected-org (get-in settings [:add-projects :selected-org :login])
            repo-key (gstring/format "%s.%s"
                                     selected-org
                                     (get-in settings [:add-projects :selected-org :type]))
            repos (get-in user [:repos repo-key])]
        (html
         [:div#add-projects
          (when (seq (user-model/missing-scopes user))
            (missing-scopes-notice (:github_oauth_scopes user) (user-model/missing-scopes user)))
          [:div.sidebar
           (om/build org-sidebar {:user user
                                  :settings settings})]
          [:div.project-listing
           [:div.overview
            [:h3 "Start following your projects"]
            [:p
             "Choose a repo in GitHub from one of your organizations, your own repos, or repos you share with others, and we'll watch it for you. We'll show you the first build immediately, and a new build will be initiated each time someone pushes commits; come back here to follow more projects."]]


           (om/build repo-filter settings)

           (om/build main {:user user
                           :repos repos
                           :selected-org selected-org
                           :settings settings})]
          [:div.sidebar]])))))
