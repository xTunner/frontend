(ns frontend.components.pieces.checkbox
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [frontend.components.pieces.button :as button]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component html]]))

(defn checkbox
  "<DOC STRING GOES here>."
  [{:keys [id value checked? disabled?] :or {}} owner]
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
                   :disabled disabled?
                   :type "checkbox"
                   :onClick #(do
                               ;; TODO -ac Hmmm, this doesn't seem to actually update anything ...
                               (om/update-state! owner :checked? (fn [checked?] (not checked?)))
                               (.log js/console (:checked? state)))}])))))

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

