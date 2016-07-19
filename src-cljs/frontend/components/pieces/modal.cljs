(ns frontend.components.pieces.modal
  (:require [frontend.components.common :as common]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [component element html]]))

(defn modal [{:keys [shown? title body close-fn error-message]}]
  (component
    (js/React.createElement
     js/React.addons.CSSTransitionGroup
     #js {:transitionName "modal"
          :transitionAppear true
          :transitionAppearTimeout 500
          :transitionEnterTimeout 500
          :transitionLeaveTimeout 500}
     (when shown?
       (element :modal
         (html
          [:div {:on-click #(when (= (.-target %) ( .-currentTarget %))
                              (close-fn))}
           [:.box
            [:.header
             [:.modal-component-title title]
             [:i.material-icons.close-button {:on-click close-fn} "clear"]]
            [:.body
             (om/build common/flashes error-message)
             body]]]))))))
