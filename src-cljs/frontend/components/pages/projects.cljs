(ns frontend.components.pages.projects
  (:require [frontend.api :as api]
            [frontend.components.pieces.org-picker :as org-picker]
            [frontend.routes :as routes]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn- table [projects owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:table {:data-component `table}
        [:thead
         [:tr
          [:th "Project"]
          [:th.right "Team Members"]
          [:th.right "Settings"]]]
        [:tbody
         (for [project projects
               :when (< 0 (count (:followers project)))]
           [:tr
            [:td (vcs-url/repo-name (:vcs_url project))]
            [:td.right (count (:followers project))]
            [:td.right [:a {:href (routes/v1-project-settings-path {:vcs_type (vcs-url/vcs-type (:vcs_url project))
                                                                    :org (vcs-url/org-name (:vcs_url project))
                                                                    :repo (vcs-url/repo-name (:vcs_url project))})}
                        [:i.material-icons "settings"]]]])]]))))

(defn- organization-ident
  "Builds an Om Next-like ident for an organization."
  [org]
  ;; Om Next will not support composite keys like this. We'll need to make a
  ;; simple unique id available on the frontend for Om Next.
  [:organization/by-vcs-type-and-name
   [(:vcs_type org) (:login org)]])

(defn page [app owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-org-ident nil})

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
           [:.card
            (om/build org-picker/picker
                      {:orgs (:organizations user)
                       :selected-org (first (filter #(= selected-org-ident (organization-ident %)) (:organizations user)))
                       :on-org-click #(om/set-state! owner :selected-org-ident (organization-ident %))})]]
          [:.main
           [:.card
            [:.header
             [:.title
              (:name selected-org)
              (case (:vcs_type selected-org)
                "github" [:i.octicon.octicon-mark-github]
                "bitbucket" [:i.fa.fa-bitbucket]
                nil)]]
            (when selected-org
              (om/build table (:projects selected-org)))]]])))))
