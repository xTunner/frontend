(ns frontend.components.pieces.input-modal
  (:require [frontend.components.inputs :as inputs]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.form :as form]
            [frontend.components.pieces.modal :as modal]
            [frontend.state :as state]
            [frontend.utils :as utils :refer-macros [html]]
            [om.core :as om]))

(defn- edit-input
  [owner label event]
  (om/set-state! owner label (.. event -target -value)))

(defn- editable-text-field
  [owner label]
  (om/build form/text-field
            {:label      label
             :required   true
             :value      (om/get-render-state owner label)
             :auto-focus true
             :on-change  (partial edit-input owner label)}))

(defn- handle-close
  [close-fn & [successful?]]
  (close-fn))

(defn- on-submit-click
  [submit-fn close-fn owner labels]
  (submit-fn
    (partial handle-close close-fn)
    (map (partial om/get-render-state owner)
         labels)))

(defn mk-close-fn
  "Used to pass control of the modal being open or closed to the parent.
  Intended for the result to be passed into `input-modal` as the `:close-fn`."
  [owner k]
  (fn []
    (om/set-state! owner k false)))

(defn input-modal
  "Creates an easy to use input modal.
  :title
    - title for the modal
  :text
    - useful flavour text to explain to users what they're doing
  :field-labels
    - [String, ...] representing the input fields you want in your modal.
  :submit-text
    - Text to populate the submit button
  :submit-fn
    - a function taking 2 values:
      - a callback function expected to be called at the completion of your
        submission. Optionally takes an argument truthy or falsey to
        indicate success or failure.
      - an array of values respectively representing the values of the
        field-labels
  :close-fn
    - a function which when called will close the modal. Typically made
      using `mk-close-fn`. For example:
      ```
      (when (:show-modal? owner-state)
        (om/build input-modal {:close-fn (mk-close-fn owner :show-modal?)
                               ...}))
      ```"
  [{title        :title
    text         :text
    field-labels :labels
    submit-text  :submit-text
    submit-fn    :submit-fn
    close-fn     :close-fn}
   owner]
  (reify
    om/IRender
    (render [_]
      (modal/modal-dialog
        {:title    title
         :body     (html
                     [:div
                      text
                      (apply form/form {}
                             (for [label field-labels]
                               (editable-text-field owner label)))])
         :actions  [(button/button {:on-click #(close-fn)
                                    :kind     :flat}
                                   "Cancel")
                    (button/managed-button {:failed-text  "Failed"
                                            :success-text "Added"
                                            :loading-text "Adding..."
                                            :kind         :primary
                                            :on-click     #(on-submit-click
                                                             submit-fn close-fn
                                                             owner field-labels)}
                                           submit-text)]
         :close-fn #(close-fn)}))))
