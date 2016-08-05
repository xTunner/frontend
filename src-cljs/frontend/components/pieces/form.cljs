(ns frontend.components.pieces.form
  (:require [devcards.core :as dc :refer-macros [defcard]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component element html]]))

(defn- text-type-field [{:keys [label validation-error field-fn]} owner]
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
           (field-fn id)
           (when validation-error
             [:.validation-error validation-error])]])))))

(defn text-field
  "A text field, with a label.

  :label            - The label for the field.
  :value            - The value of the field.
  :on-change        - An on-change handler for the field. Receives the change
                      event.
  :size             - The size of the field. One of #{:full :medium}.
                      (default: :full)
  :validation-error - (optional) A validation error to display. If given, the
                      field will appear as invalid.
  :disabled?        - (optional) If true, the field is disabled.
                      (default: false)
  :auto-complete    - (optional) If given, the value of the field's
                      :auto-complete attribute."
  [{:keys [label value on-change size validation-error disabled? auto-complete] :or {size :full}}]
  (component
    (om/build text-type-field
              {:label label
               :validation-error validation-error
               :field-fn
               (fn [id]
                 (element :input
                   (html
                    ;; This `.dumb` class opts us out of old global form styles. We can
                    ;; remove it once those styles are dead.
                    [:input.dumb
                     (cond-> {:id id
                              :class (remove nil? [(when validation-error "invalid")
                                                   (case size
                                                     :full nil
                                                     :medium "medium")])
                              :type "text"
                              :value value
                              :disabled disabled?
                              :on-change on-change}
                       auto-complete (assoc :auto-complete auto-complete))])))})))

(defn text-area
  "A text area, with a label.

  :label            - The label for the field.
  :value            - The value of the field.
  :on-change        - An on-change handler for the field. Receives the change
                      event.
  :validation-error - (optional) A validation error to display. If given, the
                      field will appear as invalid.
  :disabled?        - (optional) If true, the field is disabled.
                      (default: false)"
  [{:keys [label value on-change validation-error disabled? size] :or {size :full}}]
  (component
    (om/build text-type-field
              {:label label
               :validation-error validation-error
               :field-fn
               (fn [id]
                 (element :textarea
                   (html
                    ;; This `.dumb` class opts us out of old global form styles. We can
                    ;; remove it once those styles are dead.
                    [:textarea.dumb {:id id
                                     :class (when validation-error "invalid")
                                     :value value
                                     :disabled disabled?
                                     :on-change on-change}])))})))

(dc/do
  (defcard text-field
    (fn [state]
      (text-field {:label "Hostname"
                   :value (:value @state)
                   :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value "circleci.com"})

  (defcard text-field-medium
    (fn [state]
      (text-field {:label "Hostname"
                   :value (:value @state)
                   :on-change #(swap! state assoc :value (.. % -target -value))
                   :size :medium}))
    {:value "circleci.com"})

  (defcard text-field-disabled
    (fn [state]
      (text-field {:label "Hostname"
                   :value (:value @state)
                   :disabled? true
                   :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value "circleci.com"})

  (defcard text-field-invalid
    (fn [state]
      (text-field {:label "Hostname"
                   :value (:value @state)
                   :validation-error "\"foobar.example\" is not a valid hostname."
                   :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value "foobar.example"})

  (def lorem-ipsum
    "Lorem ipsum dolor sit amet, cum expetenda maluisset comprehensam et. Has no eirmod labores disputando, diam atqui ius at, has fugit pertinacia reprimique te. Habeo partem tamquam qui et, eam ei inermis accommodare, te enim fugit utamur mea. Ius rebum nominavi indoctum ne.

Assum consul gubergren usu eu. Ei aeterno ancillae mea, nam ne veri molestie consectetuer. Eum habeo omnes exerci ut, ea malorum feugait scripserit vix. Nobis pericula est id. Cu atqui feugait vix, solum tamquam malorum cu eos, duo quaeque disputando ne. Sit et maiestatis comprehensam, no mea etiam regione definitiones, vis ornatus omittam in. Simul expetendis inciderint no has.

Eros ridens at quo, vix an alienum democritum. Ea eos adhuc lorem dicam, omnis sanctus ad sit. Quas elitr adipisci sed id, nec ei aeque apeirian invenire. Eam et congue aliquip urbanitas, ius cu laudem omnesque suscipiantur, posse voluptatibus usu et.

Sea ei equidem torquatos, duo in quis porro voluptaria, quo eligendi recusabo intellegat ne. Nam an sint detraxit volutpat. Mei congue nominavi sadipscing ea, per amet accusata scribentur at, enim nonumy facilisis ex pro. Eam molestie salutandi democritum no, vulputate forensibus sed ei, vix aperiri impedit dissentiunt ad. Mutat postea ad cum.")

  (defcard text-area
    (fn [state]
      (text-area {:label "Favorite Latin Passage"
                  :value (:value @state)
                  :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value lorem-ipsum})

  (defcard text-area-disabled
    (fn [state]
      (text-area {:label "Favorite Latin Passage"
                  :value (:value @state)
                  :disabled? true
                  :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value lorem-ipsum})

  (defcard text-area-invalid
    (fn [state]
      (text-area {:label "Favorite Latin Passage"
                  :value (:value @state)
                  :validation-error "That's not even real Latin."
                  :on-change #(swap! state assoc :value (.. % -target -value))}))
    {:value lorem-ipsum}))
