(ns frontend.components.pages.projects
  (:require [frontend.api :as api]
            [frontend.components.common :as common]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.org-picker :as org-picker]
            [frontend.components.pieces.table :as table]
            [frontend.components.templates.main :as main-template]
            [frontend.models.project :as project-model]
            [frontend.utils :as utils :include-macros true]
            [frontend.routes :as routes]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs :as vcs-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component element html]]))

(defn- table [projects plan]
  (om/build table/table
            {:rows projects
             :columns [{:header "Project"
                        :cell-fn #(vcs-url/repo-name (:vcs_url %))}

                       {:header "Parallelism"
                        :type #{:right :shrink}
                        :cell-fn #(html
                                    (let [parallelism (project-model/parallelism %)
                                          buildable-parallelism (when plan (project-model/buildable-parallelism plan %))
                                          vcs-url (:vcs_url %)]
                                      [:a {:href (routes/v1-project-settings-path {:vcs_type (-> vcs-url vcs-url/vcs-type routes/->short-vcs)
                                                                                   :org (vcs-url/org-name vcs-url)
                                                                                   :repo (vcs-url/repo-name vcs-url)
                                                                                   :_fragment "parallel-builds"})}
                                       parallelism "x"
                                       (when buildable-parallelism (str " out of " buildable-parallelism "x"))]))}

                       {:header "Team"
                        :type #{:right :shrink}
                        :cell-fn #(count (:followers %))}

                       {:header "Settings"
                        :type #{:right :shrink}
                        :cell-fn
                        #(html
                          (let [vcs-url (:vcs_url %)]
                            [:a {:href (routes/v1-project-settings-path {:vcs_type (vcs-url/vcs-type vcs-url)
                                                                         :org (vcs-url/org-name vcs-url)
                                                                         :repo (vcs-url/repo-name vcs-url)})}
                             [:i.material-icons "settings"]]))}]}))

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
                                            "organization (or username) to view your projects.")}))))

(defn- no-projects-available [org]
  (component
    (empty-state/empty-state {:icon (html [:i.material-icons "book"])
                              :heading (html
                                        [:span
                                         (empty-state/important (:name org))
                                         " has no projects building on CircleCI"])
                              :subheading "Let's fix that by adding a new project."
                              :action (html
                                       [:a.btn.btn-primary
                                        {:href (routes/v1-add-projects)}
                                        "Add Project"])})))

(defn- organization-ident
  "Builds an Om Next-like ident for an organization."
  [org]
  ;; Om Next will not support composite keys like this. We'll need to make a
  ;; simple unique id available on the frontend for Om Next.
  [:organization/by-vcs-type-and-name
   [(:vcs_type org) (:login org)]])

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
        (let [[_ [vcs-type name]] (:selected-org-ident next-state)
              api-ch (om/get-shared owner [:comms :api])]
          (api/get-org-plan-normalized name vcs-type api-ch)
          (api/get-org-settings-normalized name vcs-type api-ch))))

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
                         :on-org-click (fn [{:keys [login vcs_type] :as org}]
                                         (om/set-state! owner :selected-org-ident (organization-ident org))
                                         ((om/get-shared owner :track-event) {:event-type :org-clicked
                                                                              :properties {:view :projects
                                                                                           :login login
                                                                                           :vcs_type vcs_type}}))})
              (html [:div.loading-spinner common/spinner])))]
          [:.main
           ;; TODO: Pulling these out of the ident is a bit of a hack. Instead,
           ;; we should pull them out of the selected-org itself. We can do that
           ;; once the selected-org and the orgs in org list are backed by the
           ;; same normalized data.
           ;;
           ;; Once they are, we'll have a value for selected-org here, but it
           ;; will only have the keys the org list uses (which includes the
           ;; vcs-type and the name). The list of projects will still be missing
           ;; until it's loaded by an additional API call.
           (if-let [[_ [vcs-type name]] selected-org-ident]
             (card/titled {:title (html
                                   [:span
                                    name
                                    (case vcs-type
                                      "github" [:i.octicon.octicon-mark-github]
                                      "bitbucket" [:i.fa.fa-bitbucket]
                                      nil)])}
              (if-let [projects (:projects selected-org)]
                (if-let [projects-with-followers
                         (seq (filter #(< 0 (count (:followers %))) projects))]
                  (table projects-with-followers (:plan selected-org))
                  (no-projects-available selected-org))
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
                                   {:href (routes/v1-add-projects)
                                    :on-click #((om/get-shared owner :track-event)
                                                {:event-type :add-project-clicked
                                                 :properties {:view :projects}})}
                                   "Add Project"])}))))
