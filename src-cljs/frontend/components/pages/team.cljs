(ns frontend.components.pages.team
  (:require [frontend.api :as api]
            [frontend.components.common :as common]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.org-picker :as org-picker]
            [frontend.components.pieces.table :as table]
            [frontend.components.templates.main :as main-template]
            [frontend.routes :as routes]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs :as vcs-utils]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component element html]]))

(defn- table [users]
  (om/build table/table
            {:rows users
             :columns [{:header "Login"
                        :cell-fn :login}

                       {:header "Projects Followed"
                        :type #{:right :shrink}
                        :cell-fn ::follow-count}]}))

(defn- no-org-selected [available-orgs bitbucket-enabled?]
  (component no-org-selected
    (card/basic
     (empty-state/empty-state {:icon (if-let [orgs (seq (take 3 available-orgs))]
                                       (element :avatars
                                         (html
                                          [:div
                                           (for [org orgs]
                                             [:img {:src (gh-utils/make-avatar-url org :size 60)}])]))
                                       (html [:i.material-icons "group"]))
                               :heading (html
                                         [:span
                                          "Get started by selecting your "
                                          (empty-state/important "organization")])
                               :subheading (str
                                            "Select your GitHub "
                                            (when bitbucket-enabled? "or Bitbucket ")
                                            "organization (or username) to view your team.")}))))

(defn- organization-ident
  "Builds an Om Next-like ident for an organization."
  [org]
  ;; Om Next will not support composite keys like this. We'll need to make a
  ;; simple unique id available on the frontend for Om Next.
  [:organization/by-vcs-type-and-name
   [(:vcs_type org) (:login org)]])

(defn- add-follow-counts [users projects]
  (for [user users
        :let [followings
              (group-by :follower
                        (for [project projects
                              follower (:followers project)]
                          {:follower follower
                           :project project}))]]
    (assoc user ::follow-count (count (get followings user)))))

(defn- main-content [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-org-ident nil})

    om/IWillMount
    (will-mount [_]
      (api/get-orgs (om/get-shared owner [:comms :api]) :include-user? true))

    ;; Emulate Om Next queries: Treat :selected-org-ident like a query param,
    ;; and when it changes, re-read the query. That is, in this case, fetch from
    ;; the API.
    om/IWillUpdate
    (will-update [_ _ next-state]
      (when (not= (:selected-org-ident (om/get-render-state owner))
                  (:selected-org-ident next-state))
        (let [[_ [vcs-type name]] (:selected-org-ident next-state)]
          (api/get-org-settings-normalized name vcs-type (om/get-shared owner [:comms :api])))))

    om/IRenderState
    (render-state [_ {:keys [selected-org-ident]}]
      (let [user (:current-user app)
            selected-org (when selected-org-ident (get-in app selected-org-ident))
            available-orgs (:organizations user)]
        (html
         [:div {:data-component `page}
          [:.sidebar
           (card/basic
            (if available-orgs
              (om/build org-picker/picker
                        {:orgs available-orgs
                         :selected-org (first (filter #(= selected-org-ident (organization-ident %)) available-orgs))
                         :on-org-click #(om/set-state! owner :selected-org-ident (organization-ident %))})
              (html [:div.loading-spinner common/spinner])))]
          [:.main
           (if-let [[_ [vcs-type name]] selected-org-ident]
             (card/titled
              (html
               [:span
                name
                (case vcs-type
                  "github" [:i.octicon.octicon-mark-github]
                  "bitbucket" [:i.fa.fa-bitbucket]
                  nil)])
              (if-let [users (:users selected-org)]
                (table (add-follow-counts users (:projects selected-org)))
                (html [:div.loading-spinner common/spinner])))
             (no-org-selected available-orgs (vcs-utils/bitbucket-enabled? user)))]])))))

(defn page [app owner]
  (reify
    om/IRender
    (render [_]
      (om/build main-template/template
                {:app app
                 :main-content (om/build main-content app)
                 :header-actions (html
                                  [:a.btn.btn-primary
                                   {:href (routes/v1-invite-teammates)}
                                   "Invite Teammates"])}))))
