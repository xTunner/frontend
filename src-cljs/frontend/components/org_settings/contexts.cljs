(ns frontend.components.org-settings.contexts
  (:require [om.core :as om]
            [frontend.state :as state]
            [frontend.components.pieces.card :as card]
            [frontend.utils :refer-macros [html]]
            [frontend.api.contexts :as api]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.empty-state :as empty-state]
            [frontend.components.pieces.table :as table]))

(def ^:private envvar-table-columns
  [{:header  "Variable"
    :cell-fn :variable}
   {:header  "Value"
    :cell-fn :value}
   {:header  "Created At"
    :cell-fn :created-at}])

(defn details
  [{resources :resources
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
                  :columns envvar-table-columns}))]))

(defn details-card
  [target-context]
  (when target-context
    (card/titled
      {:title (str "Default Context: org-global")}
      (details target-context))))

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
      om/IDidMount
      (did-mount [_]
        (api/fetch-context api-ch organization))
      om/IRender
      (render [_]
        (let [context-data (get-in app state/org-contexts-path)
              cards [(description-card)
                     (create-card api-ch organization context-data)
                     (details-card context-data)]]
          (html
            [:div
             [:div.followed-projects.row-fluid
              [:article
               [:legend
                (str "Contexts for " org-name)]
               (card/collection cards)]]]))))))
