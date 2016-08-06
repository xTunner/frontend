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
            [frontend.components.pieces.text-input :as text-input]
            [frontend.components.templates.main :as main-template]
            [frontend.components.pieces.checkbox :as checkbox]
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
          (api/get-org-settings-normalized name vcs-type api-chan)
          (api/get-org-members api-chan (:name selected-org)))
        (when (and selected-org
                   invite-teammates?)
          ;; TODO -ac here or up there? ^
          ;; (api/get-org-members api-chan (:name selected-org)))
          )))

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
              (html
               [:div
                name
                (case vcs-type
                  "github" [:i.octicon.octicon-mark-github]
                  "bitbucket" [:i.fa.fa-bitbucket]
                  nil)
                (when (:invite-teammates? (om/get-render-state owner))
                  (component
                    (modal/modal-dialog {:title "Invite Teammates"
                                       :body [:div
                                              "These are the people who are not using CircleCI yet:"
                                              [:div.constraining-modal
                                               (om/build table/table {:rows (seq (remove :circle_member (:github-users (:invite-data app))))
                                                                      :columns [{:header "Username"
                                                                                 :cell-fn (fn [user-map]
                                                                                            [:span
                                                                                             [:img.invite-gravatar {:src (gh-utils/make-avatar-url user-map :size 50)}]
                                                                                             ;; TODO -ac Uh, these spaces are a hack
                                                                                             ;; should fix with padding on gravatar
                                                                                             (str "  " (:login user-map))])}
                                                                                {:header "Email"
                                                                                 :cell-fn (fn [user-map]
                                                                                            (let [{:keys [avatar_url email login index]} user-map
                                                                                                  id-name (str login "-email")]
                                                                                              (component
                                                                                                (om/build text-input/text-input {:input-type "email"
                                                                                                                                 :on-change #(do
                                                                                                                                               (utils/edit-input owner (conj (state/invite-github-user-path index) :email) %)
                                                                                                                                               (om/update-state! owner :email (fn [email]
                                                                                                                                                                                email))
                                                                                                                                               ;; TODO -ac Hmmm, seems to be delayed by 1 character input - follow up by checking state???
                                                                                                                                               (.log js/console id-name id-name "email : " email))
                                                                                                                                 :required? true
                                                                                                                                 :id id-name
                                                                                                                                 :value email
                                                                                                                                 :defaultValue email}))))}
                                                                                {:type :shrink
                                                                                 :cell-fn (fn [user-map]
                                                                                            (let [{:keys [email login index]} user-map
                                                                                                  id-name (str login "-checkbox")]
                                                                                              (component
                                                                                                (om/build checkbox/checkbox {:id id-name
                                                                                                                             :checked? (not (nil? email))
                                                                                                                             :on-click #(do
                                                                                                                                          (utils/toggle-input owner (conj (state/invite-github-user-path index) :checked) %)
                                                                                                                                          (om/update-state! owner :checked? (fn [checked?]
                                                                                                                                                                              (not checked?)))
                                                                                                                                          (.log js/console id-name "checked? : " (om/get-state owner :checked?)))}))))}]})]]
                                       :actions [(button/button {:on-click #(om/set-state! owner :invite-teammates? false)} "Cancel")
                                                 (button/button {:on-click #(.log js/console "Send the emails in this on-click!")
                                                                 :primary? true} "Send emails")]
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
                  "Invite Teammates")])
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
