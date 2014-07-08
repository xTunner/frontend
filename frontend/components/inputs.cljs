(ns frontend.components.inputs
  (:require [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]))

;; This lets us access state in a child component that we don't want to pass through the entire
;; nested component chain.
;;
;; Example usage:
;;
;; (defn my-component [app owner]
;;   (reify
;;     om/IDidMount (did-mount [_] (inputs/did-mount owner))
;;     om/IWillUnmount (will-unmount [_] (inputs/will-unmount owner))
;;     om/IRender
;;     (render [_]
;;       (let [inputs (inputs/get-inputs-from-app-state)
;;             my-value (:my-value inputs)]
;;         (html
;;          [:form
;;           [:input {:value my-value
;;                    :on-click #(put! controls-ch [:my-input-clicked {:my-value my-value}])}]])))))
;;
;; Inputs are cleared out on page transitions, you should also clear them out on successful submits, e.g.
;; and to your controls-handler: (put! controls-ch [:clear-inputs {:paths [[:my-value]]}])

(defn did-mount
  "Sets up app watcher to update the component when the input-state changes"
  [owner]
  (let [watcher-uuid (utils/uuid)
        app-state (om/get-shared owner [:_app-state-do-not-use])]
    (om/set-state! owner [:app-state-inputs-watcher-uuid] watcher-uuid)
    (om/set-state! owner state/inputs-path (get-in @app-state state/inputs-path))
    (add-watch app-state watcher-uuid (fn [_ _ old new]
                                        (when (not= (get-in old state/inputs-path)
                                                    (get-in new state/inputs-path))
                                          (om/set-state! owner state/inputs-path (get-in new state/inputs-path)))))))

(defn will-unmount
  "Clears app watcher"
  [owner]
  (let [app-state (om/get-shared owner [:_app-state-do-not-use])
        watcher-uuid (om/get-state owner [:app-state-watcher-uuid])]
    (remove-watch app-state watcher-uuid)))

(defn get-inputs-from-app-state
  "Helper function to get the inputs that we've replicated in the component owner"
  [owner]
  (om/get-state owner state/inputs-path))
