(ns frontend.components.pages.team
  (:require [frontend.api :as api]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.form :as form]
            [frontend.components.pieces.modal :as modal]
            [frontend.components.pieces.org-picker :as org-picker]
            [frontend.components.pieces.table :as table]
            [frontend.components.templates.main :as main-template]
            [frontend.components.invites :as invites]
            [frontend.components.pieces.button :as button]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true :refer [valid-email?]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs :as vcs-utils]
            [goog.string :as gstr]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component element html]]))

(defn- table [users]
  (om/build table/table
            {:rows users
             :key-fn :login
             :columns [{:header "Login"
                        :cell-fn :login}

                       {:header "Projects Followed"
                        :type #{:right :shrink}
                        :cell-fn ::follow-count}]}))

(defn- no-org-selected [available-orgs bitbucket-enabled?]
  (component
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

(defn invitees [users-by-login]
  (->> users-by-login
       vals
       (filter (fn [user]
                 (and (:selected user)
                      (:email user))))))

(defn invite-teammates-modal [{:keys [selected-org close-fn show-modal?]} owner]
  (letfn [(select-user! [{:keys [handle] :as user}]
            (om/set-state! owner [:org-members-by-handle handle :selected] true))
          (deselect-user! [{:keys [handle] :as user}]
            (om/set-state! owner [:org-members-by-handle handle :selected] false))
          (selected? [{:keys [handle] :as user}]
            (om/get-state owner [:org-members-by-handle handle :selected]))
          (set-email! [{:keys [handle] :as user} value]
            (om/set-state! owner [:org-members-by-handle handle :email] value))
          (get-email [{:keys [handle] :as user}]
            (om/get-state owner [:org-members-by-handle handle :email]))]
    (reify
      om/IWillReceiveProps
      (will-receive-props [_ new-props]
        (let [new-selected-org (:selected-org new-props)
              new-vcs-users (:vcs-users new-selected-org)
              old-vcs-users (get-in (om/get-props owner) [:selected-org :vcs-users])
              new-show-modal? (:show-modal? new-props)]
          (when (and new-show-modal?
                     (not new-vcs-users))
            (api/get-org-members (:name new-selected-org) (:vcs_type new-selected-org) (om/get-shared owner [:comms :api])))
          (when (not= new-vcs-users old-vcs-users)
            (om/set-state! owner
                           :org-members-by-handle
                           (into {}
                                 (for [[handle {:keys [is_user email provider_id]
                                                :as user}] new-vcs-users
                                       :when (not is_user)
                                       :let [trimmed-email (some-> email gstr/trim)]]
                                   [handle {:email trimmed-email
                                            :login handle
                                            :id provider_id
                                            :selected (valid-email? trimmed-email)}]))))))
      
      om/IRenderState
      (render-state [_ {:keys [org-members-by-handle]}]
        (component
         (if show-modal?
           (let [users (when show-modal?
                         (->> selected-org
                              :vcs-users
                              vals
                              (remove :is_user)
                              (sort-by :handle)))
                 count-users (count users)
                 count-with-email (count (filter (fn [[_ user]]
                                                   (-> user :email valid-email?))
                                                 org-members-by-handle))
                 count-selected (count (filter (fn [[_ user]]
                                                 (:selected user))
                                               org-members-by-handle))]
             (modal/modal-dialog {:title "Invite Teammates"
                                  :body
                                  (element :body
                                           (html
                                            [:div
                                             (if-not (contains? selected-org :vcs-users)
                                               [:div.loading-spinner common/spinner]
                                               (list
                                                [:.header
                                                 "These are the people who are not using CircleCI yet ("
                                                 [:span
                                                  [:b count-with-email]
                                                  " of "
                                                  [:b count-users]
                                                  " users have emails, "
                                                  [:b count-selected]
                                                  " are selected):"]]
                                                [:.table
                                                 (om/build table/table
                                                           {:rows users
                                                            :key-fn :handle
                                                            :columns [{:header "Username"
                                                                       :cell-fn (fn [{:keys [handle] :as user}]
                                                                                  (element :avatars
                                                                                           (html
                                                                                            [:div
                                                                                             [:img.invite-gravatar {:src (gh-utils/make-avatar-url user
                                                                                                                                                   :size 50)}]
                                                                                             (str "  " handle)])))}
                                                                      {:header "Email"
                                                                       :cell-fn (fn [user]
                                                                                  (let [is-selected (selected? user)
                                                                                        email (get-email user)]
                                                                                    (om/build form/text-field
                                                                                              {:on-change (fn [event]
                                                                                                            (let [trimmed-input (gstr/trim (.. event -currentTarget -value))
                                                                                                                  is-valid (valid-email? trimmed-input)]
                                                                                                              (set-email! user trimmed-input)
                                                                                                              (cond
                                                                                                                (and (not is-selected)
                                                                                                                     is-valid)
                                                                                                                (select-user! user)
                                                                                                                (and is-selected
                                                                                                                     (not is-valid))
                                                                                                                (deselect-user! user))))
                                                                                               :value email
                                                                                               :size :medium
                                                                                               :validation-error (when (and (or is-selected
                                                                                                                                (not (empty? email)))
                                                                                                                            (not (valid-email? email)))
                                                                                                                   (str email " is not a valid email"))})))}
                                                                      {:type :shrink
                                                                       :cell-fn (fn [user]
                                                                                  (let [email (get-email user)
                                                                                        is-valid (valid-email? email)]
                                                                                    [:input {:type "checkbox"
                                                                                             :disabled (and (not is-valid)
                                                                                                            (not (empty? email)))
                                                                                             :checked (selected? user)
                                                                                             :on-click #(if-let [is-checked (.. % -currentTarget -checked)]
                                                                                                          (when is-valid
                                                                                                            (select-user! user))
                                                                                                          (deselect-user! user))}]))}]
                                                            :striped? true})]))]))
                                  :actions [(button/button {:on-click close-fn} "Cancel")
                                            (forms/managed-button
                                             [:button.btn.btn-success {:data-success-text "Sent"
                                                                       :on-click #(do
                                                                                    (raise! owner [:invited-github-users {:invitees (invitees org-members-by-handle)
                                                                                                                          :vcs_type (:vcs_type selected-org)
                                                                                                                          :org-name (:name selected-org)}])
                                                                                    (close-fn))
                                                                       :disabled (or (empty? (invitees org-members-by-handle))
                                                                                     (not (every? (comp valid-email? :email) (invitees org-members-by-handle))))}
                                              "Send Invites "
                                              [:i.fa.fa-envelope-o]])]
                                  :close-fn close-fn}))
           (html [:div])))))))

(defn- main-content [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-org-ident nil
       :show-modal? nil})

    om/IWillMount
    (will-mount [_]
      (api/get-orgs (om/get-shared owner [:comms :api]) :include-user? true))

    ;; Emulate Om Next queries: Treat :selected-org-ident like a query param,
    ;; and when it changes, re-read the query. That is, in this case, fetch from
    ;; the API.
    om/IWillUpdate
    (will-update [_ _ {:keys [selected-org-ident]}]
      (let [[_ [vcs-type name]] selected-org-ident
            api-chan (om/get-shared owner [:comms :api])
            selected-org (when selected-org-ident (get-in app selected-org-ident))]
        (when (not= (:selected-org-ident (om/get-render-state owner))
                    selected-org-ident)
          (api/get-org-settings-normalized name vcs-type api-chan))))

    om/IRenderState
    (render-state [_ {:keys [selected-org-ident show-modal?]}]
      (let [user (:current-user app)
            selected-org (when selected-org-ident (get-in app selected-org-ident))
            available-orgs (filter :org (:organizations user))]
        (html
          [:div {:data-component `page}
           [:.sidebar
            (card/basic
              (if available-orgs
                (om/build org-picker/picker
                          {:orgs available-orgs
                           :selected-org (first (filter #(= selected-org-ident (organization-ident %)) available-orgs))
                           :on-org-click (fn [{:keys [login vcs_type] :as org}]
                                           (om/set-state! owner :selected-org-ident (organization-ident org))
                                           ((om/get-shared owner :track-event) {:event-type :org-clicked
                                                                                :properties {:view :team
                                                                                             :login login
                                                                                             :vcs_type vcs_type}}))})
                (html [:div.loading-spinner common/spinner])))]
           [:.main
            (if-let [[_ [vcs-type name]] selected-org-ident]
              (card/titled
               {:title (html
                        [:div name
                         (case vcs-type
                           "github" [:i.octicon.octicon-mark-github]
                           "bitbucket" [:i.fa.fa-bitbucket]
                           nil)])
                :action [:div
                         (button/button
                          {:primary? true
                           :on-click #(do
                                        (om/set-state! owner :show-modal? true)
                                        ((om/get-shared owner :track-event)
                                         {:event-type :invite-teammates-clicked
                                          :properties {:view :team}}))}
                          "Invite Teammates")
                         (om/build invite-teammates-modal {:selected-org (select-keys selected-org [:name :vcs_type :vcs-users])
                                                           :close-fn #(om/set-state! owner :show-modal? false)
                                                           :show-modal? show-modal?})]}
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
                 :main-content (om/build main-content app)}))))
