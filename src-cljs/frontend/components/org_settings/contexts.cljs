(ns frontend.components.org-settings.contexts
  (:require [om.core :as om]
            [frontend.state :as state]
            [frontend.components.pieces.card :as card]
            [frontend.utils :refer-macros [html]]))

(defn main-component
  [app owner]
  (let [org-name (get-in app state/org-name-path)]
    (reify
      om/IRender
      (render [_]
        (html
          [:div
           [:div.followed-projects.row-fluid
            [:article
             [:legend (str "Contexts for " org-name)]
             (card/titled
               {:title "What is a Context?"}
               (html
                 [:div
                  [:p "Contexts are a set of resources that can be shared across different projects in an organization."]]))]]])))))
