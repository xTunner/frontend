(ns frontend.components.pieces.confirmation-button
  (:require [frontend.components.pieces.table :as table]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.modal :as modal]
            [frontend.components.pieces.button :as button]
            [frontend.utils :refer-macros [html]]
            [om.core :as om]))

(defn confirmation-button
  "Renders an action button which when clicked asks for confirmation before
  performing the action. Suitable for a settings table row which presents a
  confirmation dialog before actually proceeding with some action.

  :action-text
    - Text which will populate the action button.
  :confirmation-text
    - The content of the dialog. Should ask the user if they're sure
      they want to perform the action. Should clearly describe what
      is going to happen.
  :action-fn
    - The function which will actually perform the action the row."
  [{confirmation-text :confirmation-text
    action-text :action-text
    action-fn :action-fn}
   owner]
  (reify
    om/IInitState
    (init-state [_]
      {:show-modal? false})
    om/IRenderState
    (render-state [_ {:keys [show-modal?]}]
      (html
        [:span
         (table/action-button
           action-text
           (icon/cancel-circle)
           #(om/set-state! owner :show-modal? true))
         (when show-modal?
           (modal/modal-dialog {:title    "Are you sure?"
                                :body     confirmation-text
                                :actions  [(button/button {:on-click #(om/set-state! owner :show-modal? false)
                                                           :kind     :flat}
                                                          "Cancel")
                                           (button/button {:kind     :primary
                                                           :on-click action-fn}
                                                          action-text)]
                                :close-fn #(om/set-state! owner :show-modal? false)}))]))))
