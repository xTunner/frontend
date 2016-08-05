(ns frontend.components.pieces.form
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component html]]))

(defn text-field
  "A text field, with a label.

  :label            - The label for the field.
  :value            - The value of the field.
  :on-change        - An on-change handler for the field. Receives the change
                      event.
  :validation-error - (optional) A validation error to display. If given, the
                      field will appear as invalid.
  :disabled?        - (optional) If true, the field is disabled.
                      (default: false)
  :size             - The size of the field. One of #{:full :medium}.
                      (default: :full)"
  [{:keys [label value on-change validation-error disabled? size] :or {size :full}} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:id (random-uuid)})
    om/IRenderState
    (render-state [_ {:keys [id]}]
      (component
        (html
         [:div {:class (remove nil? [(when validation-error "invalid")
                                     (case size
                                       :full nil
                                       :medium "medium")])}
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

  (defcard text-field-medium
    (fn [state]
      (om/build text-field {:label "Hostname"
                            :value (:value @state)
                            :on-change #(swap! state assoc :value (.. % -target -value))
                            :size :medium}))
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
