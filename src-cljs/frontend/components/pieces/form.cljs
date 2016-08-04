(ns frontend.components.pieces.form
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component html]]))

(defn text-field [{:keys [label value validation-error disabled? on-change]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:id (random-uuid)})
    om/IRenderState
    (render-state [_ {:keys [id]}]
      (component
        (html
         [:div {:class (when validation-error "invalid")}
          [:label {:for id} label]
          [:.field
           ;; This `.dumb` class opts us out of old global form styles. We can
           ;; remove it once those styles are dead.
           [:input.dumb {:id id
                         :type "text"
                         :value value
                         :disabled disabled?
                         :on-change on-change}]
           (when validation-error
             [:.validation-error validation-error])]])))))

(dc/do
  (defcard text-field
    (fn [state]
      (om/build text-field {:label "Hostname"
                            :value (:value @state)
                            :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value "circleci.com"})

  (defcard text-field-disabled
    (fn [state]
      (om/build text-field {:label "Hostname"
                            :value (:value @state)
                            :disabled? true
                            :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value "circleci.com"})

  (defcard text-field-invalid
    (fn [state]
      (om/build text-field {:label "Hostname"
                            :value (:value @state)
                            :validation-error "\"foobar.example\" is not a valid hostname."
                            :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value "foobar.example"}))
