(ns frontend.components.org-settings.contexts
  (:require [frontend.api.contexts :as api]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.confirmation-button :as confirmation-button]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.input-modal :as input-modal]
            [frontend.components.pieces.table :as table]
            [frontend.components.project-settings :as project-envvars]
            [frontend.state :as state]
            [frontend.utils :refer-macros [html]]
            [om.core :as om]))

(defn- remove-column
  [api-ch organization]
  {:header "Remove"
   :cell-fn (fn [resource]
              (om/build confirmation-button/confirmation-button
                        {:action-text "Remove"
                         :confirmation-text
                         (str
                           "Are you sure you want to remove the resource \""
                           (:variable resource)
                           "\"?")

                         :action-fn
                         #(api/remove-resources api-ch organization [resource])}))})

(def ^:private envvar-table-columns
  [{:header  "Variable"
    :cell-fn :variable}
   {:header  "Value"
    :cell-fn :value}
   {:header  "Created At"
    :cell-fn (comp str :timestamp)}])

(defn details
  [api-ch organization
   {resources :resources
    created-at :created-at}]
  (html
    [:div
     [:p (str "This context was created at: " created-at)]
     (if (empty? resources)
       (empty-state/empty-state
         {:heading (html [:span (empty-state/important "No Resources")])
          :subheading (html
                        [:div
                         [:p "Resources are environment variables."]])})
       (om/build table/table
                 {:rows resources
                  :key-fn  :variable
                  :columns (conj envvar-table-columns
                                 (remove-column api-ch organization))}))]))

(defn details-card
  [owner organization target-context]
  (when target-context
    (card/titled
      {:title  (str "Default Context: org-global")
       :action [(button/button {:on-click #(om/set-state! owner :show-resources-modal? true)
                                :kind     :primary
                                :size     :small}
                               "Add Resources")]}
      (details (om/get-shared owner [:comms :api])
               organization
               target-context))))

(defn description-card
  []
  (card/titled
    {:title "What is a Context?"}
    (html
      [:div
       [:p "Contexts are a set of resources that can be shared across different projects in an organization."]])))

(defn create
  [api-ch organization]
  [:div
   (button/button {:on-click #(api/create-context api-ch organization)
                   :kind     :primary}
                  "Create a Context")])

(defn create-card
  [api-ch organization a-context-exists?]
  (when-not a-context-exists?
    (card/titled
      {:title "No Context Found?"}
      (create api-ch organization))))

(defn main
  [app owner]
  (let [api-ch (om/get-shared owner [:comms :api])
        organization (get-in app state/org-data-path)
        org-name (get-in app state/org-name-path)]
    (reify
      om/IInitState
      (init-state [_]
        {:show-resources-modal? nil})
      om/IDidMount
      (did-mount [_]
        (api/fetch-context api-ch organization))
      om/IRenderState
      (render-state [_ {:keys [show-resources-modal?]}]
        (let [context-data (get-in app state/org-contexts-path)
              cards [(description-card)
                     (create-card api-ch organization context-data)
                     (details-card owner organization context-data)]]
          (html
            [:div
             [:div.followed-projects.row-fluid
              [:article
               [:legend (str "Contexts for " org-name)]
               (when show-resources-modal?
                 (om/build input-modal/input-modal
                           {:title       "Add an Environment Variable"
                            :labels      ["Variable" "Value"]
                            :text        project-envvars/env-var-tutorial
                            :submit-text "Add Variable"
                            :submit-fn   (fn [callback [variable value]]
                                           (api/store-resources api-ch callback
                                                                organization [{:variable variable
                                                                               :value    value}]))
                            :close-fn    (input-modal/mk-close-fn owner :show-resources-modal?)}))
               (card/collection cards)]]]))))))
