(ns frontend.components.pieces.empty-state
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.pieces.button :as button])
  (:require-macros [frontend.utils :refer [component html]]))

(defn empty-state [{:keys [icon heading subheading action]}]
  (component empty-state
    (html
     [:div
      [:.icon icon]
      [:.heading heading]
      [:.subheading subheading]
      (when action
        [:.action action])])))

(dc/do
  (defcard empty-state
    (html
     (empty-state {:icon (html [:i.material-icons "cake"])
                   :heading (html
                             [:span
                              "The "
                              [:b "cake"]
                              " is a lie"])
                   :subheading "Let's add some."
                   :action (button/button {:primary? true}
                                          "Add Cake")}))))
