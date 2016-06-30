(ns frontend.components.pages.projects
  (:require [frontend.api :as api]
            [frontend.components.common :as common]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.org-picker :as org-picker]
            [frontend.components.pieces.table :as table]
            [frontend.components.templates.main :as main-template]
            [frontend.routes :as routes]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn- table [projects owner]
  (reify
    om/IRender
    (render [_]
      (om/build table/table
                {:rows (filter #(< 0 (count (:followers %))) projects)
                 :columns [{:header "Project"
                            :cell-fn #(vcs-url/repo-name (:vcs_url %))}

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
                                 [:i.material-icons "settings"]]))}]}))))

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
        (let [[_ [vcs-type name]] (:selected-org-ident next-state)]
          (api/get-org-settings-normalized name vcs-type (om/get-shared owner [:comms :api])))))

    om/IRenderState
    (render-state [_ {:keys [selected-org-ident]}]
      (let [user (:current-user app)
            selected-org (when selected-org-ident (get-in app selected-org-ident))]
        (html
         [:div {:data-component `page}
          [:.sidebar
           (card/basic
            (if (:organizations user)
              (om/build org-picker/picker
                        {:orgs (:organizations user)
                         :selected-org (first (filter #(= selected-org-ident (organization-ident %)) (:organizations user)))
                         :on-org-click #(om/set-state! owner :selected-org-ident (organization-ident %))})
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
           (when-let [[_ [vcs-type name]] selected-org-ident]
             (card/titled
              (html
               [:span
                name
                (case vcs-type
                  "github" [:i.octicon.octicon-mark-github]
                  "bitbucket" [:i.fa.fa-bitbucket]
                  nil)])
              (if (:projects selected-org)
                (om/build table (:projects selected-org))
                (html [:div.loading-spinner common/spinner]))))]])))))

(defn page [app owner]
  (reify
    om/IRender
    (render [_]
      (om/build main-template/template
                {:app app
                 :main-content (om/build main-content app)
                 :header-actions (html
                                  [:a.btn.btn-primary
                                   {:href (routes/v1-add-projects)}
                                   "Add Project"])}))))
