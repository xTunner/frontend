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
            [frontend.components.pieces.button :as button]
            [frontend.utils :as utils :include-macros true :refer [valid-email?]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs :as vcs-utils]
            [goog.string :as gstr]
            [inflections.core :as inflections]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component element html]]))

(defn- add-follow-counts [users projects]
  (for [user users
        :let [followings
              (group-by :follower
                        (for [project projects
                              follower (:followers project)]
                          {:follower follower
                           :project project}))]]
    (assoc user ::follow-count (count (get followings user)))))

(defn- table [{:keys [users projects] :as selected-org}]
  (let [rows (cond-> users
               projects (add-follow-counts projects))
        columns (cond-> [{:header "Login"
                          :cell-fn :login}]
                  projects (conj {:header "Projects Followed"
                                  :type #{:right :shrink}
                                  :cell-fn ::follow-count}))]
    (om/build table/table
              {:rows rows
               :key-fn :login
               :columns columns})))

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

;; functions for invite-teammates-modal component state manipulation

(defn- component-user-ident [{:keys [type handle] :as user} & keys]
  [:org-members-by-type-and-handle [type handle]])

(defn- conj-to-user-ident [user & keys]
  (apply conj (component-user-ident user) keys))

(defn- select! [owner user]
  (om/set-state! owner (conj-to-user-ident user :selected?) true))

(defn- deselect! [owner user]
  (om/set-state! owner (conj-to-user-ident user :selected?) false))

(defn- selected? [owner user]
  (om/get-state owner (conj-to-user-ident user :selected?)))

(defn- set-entered-email! [owner user value]
  (om/set-state! owner (conj-to-user-ident user :entered-email) value))

(defn- get-entered-email [owner user]
  (om/get-state owner (conj-to-user-ident user :entered-email)))

(defn- get-user-from-state [owner vcs-user]
  (om/get-state owner (component-user-ident vcs-user)))

(defn- invitees [component-state vcs-users]
  (keep (fn [{:keys [handle] :as vcs-user}]
          (let [user-ident (component-user-ident vcs-user)
                {:keys [selected?] :as component-user} (get-in component-state user-ident)]
            (when selected?
              (-> vcs-user
                  (assoc :email (:entered-email component-user))
                  (select-keys [:provider_id :handle :email :name])))))
        vcs-users))

(defn- invite-button-text [number-of-invites]
  (if (zero? number-of-invites)
    "Send Invites"
    (str "Send " (inflections/pluralize number-of-invites "Invite"))))

(defn invite-teammates-modal [{:keys [selected-org close-fn show-modal?]} owner]
  (reify
    om/IWillReceiveProps
    (will-receive-props [_ new-props]
      (let [new-selected-org (:selected-org new-props)
            new-vcs-users (:vcs-users new-selected-org)
            vcs-users (:vcs-users selected-org)
            new-show-modal? (:show-modal? new-props)]
        (when (and new-show-modal?
                   (not new-vcs-users))
          (api/get-org-members (:name new-selected-org) (:vcs_type new-selected-org) (om/get-shared owner [:comms :api])))
        (when (not= new-vcs-users vcs-users)
          (om/set-state! owner
                         (reduce (fn [new-state {:keys [user? email] :as user}]
                                   (if user?
                                     new-state
                                     (let [user-state (if-let [component-user (get-user-from-state owner user)]
                                                        component-user
                                                        (let [trimmed-email (some-> email gstr/trim)]
                                                          {:entered-email trimmed-email
                                                           :selected? (valid-email? trimmed-email)}))
                                           user-ident (component-user-ident user)]
                                       (assoc-in new-state user-ident user-state))))
                                 {}
                                 new-vcs-users)))))

    om/IRenderState
    (render-state [_ {:keys [org-members-by-type-and-handle] :as state}]
      (component
       (html
        [:div
         (when show-modal?
           (let [{:keys [vcs-users]} selected-org
                 users (remove :user? vcs-users)
                 count-users (count users)
                 count-selected (count (filter (fn [[_ user]]
                                                 (:selected? user))
                                               org-members-by-type-and-handle))
                 count-with-email (count (filter (fn [[_ user]]
                                                   (-> user :entered-email valid-email?))
                                                 org-members-by-type-and-handle))]
             (modal/modal-dialog {:title "Invite Teammates"
                                  :body
                                  (element :body
                                           (html
                                            [:div
                                             (if-not (contains? selected-org :vcs-users)
                                               [:div.loading-spinner common/spinner]
                                               (list
                                                [:.header
                                                 "These are the people who are not using CircleCI yet. "
                                                 [:span
                                                  [:b count-with-email]
                                                  " of "
                                                  [:b count-users]
                                                  " have valid email addresses."]]
                                                [:.members-table
                                                 (om/build table/table
                                                           {:rows users
                                                            :key-fn :handle
                                                            :columns [{:header "Username"
                                                                       :cell-fn (fn [{:keys [handle] :as user}]
                                                                                  (element :username
                                                                                           (html
                                                                                            [:div
                                                                                             [:img.invite-gravatar {:src (gh-utils/make-avatar-url user
                                                                                                                                                   :size 50)}]
                                                                                             (str "  " handle)])))}
                                                                      {:header "Email"
                                                                       :cell-fn (fn [user]
                                                                                  (let [selected? (selected? owner user)
                                                                                        entered-email (get-entered-email owner user)]
                                                                                    (element :email-field
                                                                                      (html
                                                                                        [:div
                                                                                         (om/build form/text-field
                                                                                              {:on-change (fn [event]
                                                                                                            (let [trimmed-input (gstr/trim (.. event -currentTarget -value))
                                                                                                                  valid? (valid-email? trimmed-input)]
                                                                                                              (set-entered-email! owner user trimmed-input)
                                                                                                              (cond
                                                                                                                (and (not selected?)
                                                                                                                     valid?)
                                                                                                                (select! owner user)
                                                                                                                (and selected?
                                                                                                                     (not valid?))
                                                                                                                (deselect! owner user))))
                                                                                               :value entered-email
                                                                                               :size :medium
                                                                                               :validation-error (when (and (or selected?
                                                                                                                                (not (empty? entered-email)))
                                                                                                                            (not (valid-email? entered-email)))
                                                                                                                   (str entered-email " is not a valid email"))})]))))}
                                                                      {:type :shrink
                                                                       :cell-fn (fn [user]
                                                                                  (let [entered-email (get-entered-email owner user)
                                                                                        valid? (valid-email? entered-email)]
                                                                                    [:input {:type "checkbox"
                                                                                             :disabled (and (not valid?)
                                                                                                            (not (empty? entered-email)))
                                                                                             :checked (selected? owner user)
                                                                                             :on-click #(if-let [checked? (.. % -currentTarget -checked)]
                                                                                                          (when valid?
                                                                                                            (select! owner user))
                                                                                                          (deselect! owner user))}]))}]
                                                            :striped? true})]))]))
                                  :actions [(button/button {:on-click close-fn} "Cancel")
                                            (button/managed-button {:success-text "Sent"
                                                                    :on-click #(do
                                                                                 (raise! owner [:invited-team-members {:invitees (invitees state vcs-users)
                                                                                                                       :vcs_type (:vcs_type selected-org)
                                                                                                                       :org-name (:name selected-org)}])
                                                                                 (close-fn))
                                                                    :kind :primary
                                                                    :disabled? (zero? count-selected)}
                                                                   (invite-button-text count-selected))]
                                  :close-fn close-fn})))])))))

(defn- main-content [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-org-ident nil
       :show-invite-modal? nil})

    om/IWillMount
    (will-mount [_]
      (api/get-orgs (om/get-shared owner [:comms :api]) :include-user? true))

    ;; Emulate Om Next queries: Treat :selected-org-ident like a query param,
    ;; and when it changes, re-read the query. That is, in this case, fetch from
    ;; the API.
    om/IWillUpdate
    (will-update [_ _ {:keys [selected-org-ident]}]
      (let [[_ [vcs-type name]] selected-org-ident
            api-chan (om/get-shared owner [:comms :api])]
        (when (not= (:selected-org-ident (om/get-render-state owner))
                    selected-org-ident)
          (api/get-org-members name vcs-type api-chan)
          (api/get-org-settings-normalized name vcs-type api-chan))))

    om/IRenderState
    (render-state [_ {:keys [selected-org-ident show-invite-modal?]}]
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
                          {:kind :primary
                           :size :medium
                           :on-click #(do
                                        (om/set-state! owner :show-invite-modal? true)
                                        ((om/get-shared owner :track-event)
                                         {:event-type :invite-teammates-clicked
                                          :properties {:view :team}}))}
                          "Invite Teammates")
                         (om/build invite-teammates-modal {:selected-org (select-keys selected-org [:name :vcs_type :vcs-users])
                                                           :close-fn #(om/set-state! owner :show-invite-modal? false)
                                                           :show-modal? show-invite-modal?})]}
               (if (:users selected-org)
                 (table (select-keys selected-org [:users :projects]))
                 (html [:div.loading-spinner common/spinner])))
              (no-org-selected available-orgs (vcs-utils/bitbucket-enabled? user)))]])))))

(defn page [app owner]
  (reify
    om/IRender
    (render [_]
      (om/build main-template/template
                {:app app
                 :main-content (om/build main-content app)}))))
