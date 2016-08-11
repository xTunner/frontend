(ns frontend.components.pages.team
  (:require [frontend.api :as api]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.modal :as modal]
            [frontend.components.pieces.org-picker :as org-picker]
            [frontend.components.pieces.table :as table]
            [frontend.components.templates.main :as main-template]
            [frontend.components.invites :as invites]
            [frontend.components.pieces.button :as button]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
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

(defn invitees
  "Filters users to invite and returns only fields needed by invitation API"
  [users]
  (->> users
       (filter (fn [u] (and (:email u)
                            (:checked u))))
       (map (fn [u] (select-keys u [:email :login :id])))
       vec))

(defn- main-content [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-org-ident nil
       :invite-teammates? false})

    om/IWillMount
    (will-mount [_]
      (api/get-orgs (om/get-shared owner [:comms :api]) :include-user? true))

    ;; Emulate Om Next queries: Treat :selected-org-ident like a query param,
    ;; and when it changes, re-read the query. That is, in this case, fetch from
    ;; the API.
    om/IWillUpdate
    (will-update [_ _ {:keys [selected-org-ident invite-teammates?]}]
      (let [[_ [vcs-type name]] selected-org-ident
            api-chan (om/get-shared owner [:comms :api])
            selected-org (when selected-org-ident (get-in app selected-org-ident))]
        (when (not= (:selected-org-ident (om/get-render-state owner))
                    selected-org-ident)
          (api/get-org-settings-normalized name vcs-type api-chan))
        (when (and selected-org
                   invite-teammates?)
          (api/get-org-members api-chan (:name selected-org)))))

    om/IRenderState
    (render-state [_ {:keys [selected-org-ident invite-teammates?]}]
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
                   :action  (html
                                (let [invite-data (:invite-data app)
                                      users (remove :circle_member (:github-users invite-data))
                                      opts {:vcs_type (:vcs_type invite-data)
                                            :org-name (:org invite-data)}
                                      count-users (count users)
                                      count-with-email (count (filter #(utils/valid-email? (:email %)) users))
                                      count-selected (count (filter #(:checked %) users))]
                                    [:div (when (:invite-teammates? (om/get-render-state owner))
                                              (component
                                                  (modal/modal-dialog {:title "Invite Teammates"
                                                                       :body
                                                                       [:div
                                                                        [:div
                                                                         "These are the people who are not using CircleCI yet:"
                                                                         [:br]
                                                                         [:span [:b count-with-email] " of " [:b count-users] " users have emails, " [:b count-selected] " are selected"]]
                                                                        [:div.constraining-modal
                                                                         (om/build table/table {:rows users
                                                                                                :key-fn :login
                                                                                                :columns [{:header "Username"
                                                                                                           :cell-fn (fn [user-map]
                                                                                                                        [:span
                                                                                                                         [:img.invite-gravatar {:src (gh-utils/make-avatar-url user-map :size 50)}]
                                                                                                                         (str "  " (:login user-map))])}
                                                                                                          {:header "Email"
                                                                                                           :cell-fn (fn [user-map]
                                                                                                                        (let [{:keys [avatar_url email login index checked]} user-map
                                                                                                                              id-name (str login "-email")
                                                                                                                              error? (and (or checked (not (empty? email)))
                                                                                                                                          (not (utils/valid-email? email)))]
                                                                                                                            [:span
                                                                                                                             [:input {:type "email"
                                                                                                                                :on-change (fn [event]
                                                                                                                                             (utils/edit-input owner (conj (state/invite-github-user-path index) :email) event)
                                                                                                                                             (when (and (not= checked true)
                                                                                                                                                        (not (empty? email)))
                                                                                                                                               (utils/toggle-input owner (conj (state/invite-github-user-path index) :checked) nil)))
                                                                                                                                :required? true
                                                                                                                                :id id-name
                                                                                                                                :value email
                                                                                                                                ;; :size "small"
                                                                                                                                ;; :long? true
                                                                                                                                ;; :error? error?
                                                                                                                                :defaultValue email}]]))}
                                                                                                          {:type :shrink
                                                                                                           :cell-fn (fn [user-map]
                                                                                                                        (let [{:keys [email login index checked]} user-map
                                                                                                                              id-name (str login "-checkbox")
                                                                                                                              email-valid? (utils/valid-email? email)]
                                                                                                                          [:input {:type "checkbox"
                                                                                                                                   :id id-name
                                                                                                                                   :disabled? (and (not (utils/valid-email? email))
                                                                                                                                                   (not (empty? email)))
                                                                                                                                   :checked? checked
                                                                                                                                   :on-click #(utils/toggle-input owner (conj (state/invite-github-user-path index) :checked) %)}]))}]
                                                                                                :striped? true
                                                                                                :fixed-header? true})]]
                                                                       :actions [(button/button {:on-click #(om/set-state! owner :invite-teammates? false)} "Cancel")
                                                                                 (forms/managed-button
                                                                                     [:button.btn.btn-success {:data-success-text "Sent"
                                                                                                               :on-click #(raise! owner [:invited-github-users (merge {:invitees (invitees users)
                                                                                                                                                                       :vcs_type (:vcs_type opts)}
                                                                                                                                                                      (if (:project-name opts)
                                                                                                                                                                          {:project-name (:project-name opts)}
                                                                                                                                                                          {:org-name (:org-name opts)}))])
                                                                                                               :disabled (or (empty? (invitees users))
                                                                                                                             (not (every? #(utils/valid-email? (:email %)) (invitees users))))}
                                                                                      "Send Invites "
                                                                                      [:i.fa.fa-envelope-o]])]
                                                                       :close-fn #(om/set-state! owner :invite-teammates? false)})))
                                     (button/button
                                         {:disabled? (nil? (:users selected-org))
                                          :primary? true
                                          :on-click (fn []
                                                        ;; TODO -ac Add tracking back in before merging
                                                        #_((om/get-shared owner :track-event)
                                                           {:event-type :invite-teammates-clicked
                                                            :properties {:view :team}})
                                                        (om/set-state! owner :invite-teammates? (not (:invite-teammates? (om/get-render-state owner)))))}
                                         "Invite Teammates")]))}
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
