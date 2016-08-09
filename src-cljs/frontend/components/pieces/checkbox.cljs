(ns frontend.components.pieces.checkbox
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.pieces.button :as button]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component html]]))

(defn checkbox
  ;; TODO -ac Add docstring
  "This little checkbox component is useful for providing a consistent,
  completely styled checkbox input field.

  :id       - (optional) A unique ID for the checkbox

  :value    - (optional) Used to provide

  :on-click - A funciton that toggles the value of :checked.

  :checked? - Used for both the :defaultChecked and :checked properties, this
              should often be used in conjunction with :on-click, to toggle
              :checked? on each click.

  disabled? - (optional) Boolean for setting the checkbox to a disabled state."
  [{:keys [id value on-click checked? disabled?] :or {}} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:checked? checked?})
    om/IRenderState
    (render-state [_ state]
      (component
        (html
          [:input {:id id
                   :value value
                   :defaultChecked (:checked? state)
                   :checked checked?
                   :disabled disabled?
                   :type "checkbox"
                   :on-click on-click}])))))

(dc/do
  (defcard various-checkboxed
    (html
     [:div
      [:div "Unchecked (default):"]
      (om/build checkbox {})
      [:div "Checked: "]
      (om/build checkbox {:checked? true})
      [:div "Disabled: "]
      (om/build checkbox {:disabled? true})])))

