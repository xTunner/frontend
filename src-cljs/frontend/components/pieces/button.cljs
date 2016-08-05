(ns frontend.components.pieces.button
  (:require [devcards.core :as dc :refer-macros [defcard]])
  (:require-macros [frontend.utils :refer [component html]]))

(defn button
  "A standard button.

  :on-click  - A function called when the button is clicked.
  :primary?  - If true, the button appears as a primary button. (default: false)
  :disabled? - If true, the button is disabled. (default: false)
  :size      - The size of the button. One of #{:full :medium}. (default: :full)"
  [{:keys [on-click primary? disabled? size] :or {size :full}} content]
  (component
    (html
     [:button {:class (remove nil? [(when primary? "primary")
                                    (case size
                                      :full nil
                                      :medium "medium")])
               :disabled disabled?
               :on-click on-click}
      content])))


(dc/do
  (defcard full-buttons
    "These are our buttons in their normal, `:full` size (the default)."
    (html
     [:div
      [:div
       (button {:primary? true
                :on-click #(js/alert "Clicked!")}
               "Primary Button")
       (button {:on-click #(js/alert "Clicked!")}
               "Secondary Button")]
      [:div
       (button {:disabled? true
                :primary? true
                :on-click #(js/alert "Clicked!")}
               "Primary Disabled")
       (button {:disabled? true
                :on-click #(js/alert "Clicked!")}
               "Secondary Disabled")]]))

  (defcard medium-buttons
    "These are our buttons in `:medium` size, used in table rows and anywhere
    vertical space is at a premium."
    (html
     [:div
      [:div
       (button {:primary? true
                :size :medium
                :on-click #(js/alert "Clicked!")}
               "Primary Button")
       (button {:size :medium
                :on-click #(js/alert "Clicked!")}
               "Secondary Button")]
      [:div
       (button {:disabled? true
                :primary? true
                :size :medium
                :on-click #(js/alert "Clicked!")}
               "Primary Disabled")
       (button {:disabled? true
                :size :medium
                :on-click #(js/alert "Clicked!")}
               "Secondary Disabled")]])))
