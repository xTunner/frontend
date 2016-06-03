(ns frontend.components.pages.projects
  (:require [om.core :as om :include-macros true]
            [frontend.components.pieces.org-picker :as org-picker])
  (:require-macros [frontend.utils :refer [html]]))

(defn page [data owner]
  (reify
    om/IInitState
    (init-state [_]
      {:selected-org nil})
    om/IRenderState
    (render-state [_ {:keys [selected-org]}]
      (let [user (:current-user data)
            settings (:settings data)
            selected-org selected-org]
        (html
         [:div.card
          (om/build org-picker/picker
                    {:orgs (:organizations user)
                     :selected-org selected-org
                     :on-org-click #(om/set-state! owner :selected-org %)})])))))
