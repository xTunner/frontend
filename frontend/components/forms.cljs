(ns frontend.components.forms
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [frontend.components.common :as common]
            [frontend.utils :as utils :include-macros true]
            [frontend.disposable :as disposable :refer [dispose]]
            [om.core :as om :include-macros true])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]
                   [frontend.utils :refer [html]]))


;; Example usage for managed-button:

;; component html:
;; [:form (forms/managed-button [:input {:type "submit" :on-click #(raise! owner [:my-control])}])]

;; controls handler:
;; (defmethod post-control-event! :my-control
;;   [target message args previous-state current-state]
;;   (let [status (do-something)
;;         uuid frontend.async/*uuid*]
;;     (forms/release-button! uuid status)))

(def registered-channels (atom {}))

(defn register-channel! [owner]
  (let [channel (chan)
        uuid (utils/uuid)]
    (swap! registered-channels assoc uuid channel)
    (om/update-state! owner [:registered-channel-uuids] #(conj (set %) uuid))
    {:channel channel :uuid uuid}))

(defn deregister-channel! [owner uuid]
  ;;XXX The `when is a hack to silently ignore updates to unmounted component state.
  (om/update-state! owner [:registered-channel-uuids] #(when % (disj % uuid)))
  (when-let [channel (get @registered-channels uuid)]
    (swap! registered-channels dissoc uuid)
    (close! channel)))

(defn release-button!
  "Used by the controls controller to set the button state. status should be a valid button state,
  :success, :failed, or :idle"
  [uuid status]
  (when-let [channel (get @registered-channels uuid)]
    (put! channel status)))

(defn append-cycle
  "Adds the button-state to the end of the lifecycle"
  [owner button-state]
  (om/update-state! owner [:lifecycle] #(conj % button-state)))

(defn wrap-managed-button-handler
  "Wraps the on-click handler with a uuid binding and registers a global channel
   so that the controls handler can communicate that it is finished with the button."
  [handler owner]
  (fn [& args]
    (append-cycle owner :loading)
    (let [{:keys [uuid channel]} (register-channel! owner)]
      (binding [frontend.async/*uuid* uuid]
        ;;XXX Async mutation of state may execute after dismount!
        (go (append-cycle owner (<! channel))
            (deregister-channel! owner uuid))
        (apply handler args)))))

(defn schedule-idle
  "Transistions the state from success/failed to idle."
  [owner lifecycle]
  ;; Clear timer, just in case. No harm in clearing nil or finished timers
  (js/clearTimeout (om/get-state owner [:idle-timer]))
  (let [cycle-count (count lifecycle)
        t (js/setTimeout
           ;; Careful not to transition to idle if the spinner somehow got
           ;; back to a loading state. This shouldn't happen, but we'll be
           ;; extra careful.
           #(om/update-state! owner [:lifecycle]
                              (fn [cycles]
                                (if (= (count cycles) cycle-count)
                                  (conj cycles :idle)
                                  cycles)))
           1000)]
    (om/set-state! owner [:idle-timer] t)))

(defn managed-button*
  "Takes an ordinary input or button hiccup form.
   Automatically disables the button until the controls handler calls release-button!"
  [hiccup-form owner]
  (reify
    om/IDisplayName (display-name [_] "Managed button")
    om/IInitState
    (init-state [_]
      {:lifecycle [:idle]
       :registered-channel-uuids #{}
       :idle-timer nil})

    om/IWillUnmount
    (will-unmount [_]
      (js/clearTimeout (om/get-state owner [:idle-timer]))
      (doseq [uuid (om/get-state owner [:registered-channel-uuids])]
        (deregister-channel! owner uuid)))

    om/IWillUpdate
    (will-update [_ _ {:keys [lifecycle]}]
      (when (#{:success :failed} (last lifecycle))
        (schedule-idle owner lifecycle)))

    om/IRenderState
    (render-state [_ {:keys [lifecycle]}]
      (let [button-state (last lifecycle)
            [tag attrs & rest] hiccup-form
            data-field (keyword (str "data-" (name button-state) "-text"))
            new-value (-> (merge {:data-loading-text "..."
                                  :data-success-text "Saved"
                                  :data-failed-text "Failed"}
                                 attrs)
                          (get data-field (:value attrs)))
            new-body (cond (= :idle button-state) rest
                           (:data-spinner attrs) common/spinner
                           :else new-value)
            new-attrs (-> attrs
                          ;; Disable the button when it's not idle
                          ;; We're changing the value of the button, so its safer not to let
                          ;; people click on it.
                          (assoc :disabled (not= :idle button-state))
                          (update-in [:class] (fn [c] (cond (= :idle button-state) c
                                                            (string? c) (str c " disabled")
                                                            (coll? c) (conj c "disabled")
                                                            :else "disabled")))
                          (update-in [:on-click] wrap-managed-button-handler owner)
                          (update-in [:value] (fn [v]
                                                (or new-value v))))]
        (html
         (vec (concat [tag new-attrs]
                      [new-body])))))))

(defn managed-button
  "Takes an ordinary input or button hiccup form.
   Disables the button while the controls handler waits for any API responses to come back.
   When the button is clicked, it replaces the button value with data-loading-text,
   when the response comes back, and the control handler calls release-button! it replaces the
   button with the data-:status-text for a second."
  [hiccup-form]
  (om/build managed-button* hiccup-form))
