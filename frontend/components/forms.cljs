(ns frontend.components.forms
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [frontend.components.common :as common]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defn tap-api
  "Sets up a tap of the api channel and watches for the API request associated with
  the form submission to complete.
  Runs success-fn if the API call was succesful and failure-fn if it failed."
  [api-mult api-tap uuid {:keys [success-fn failure-fn]}]
  (async/tap api-mult api-tap)
  (go-loop []
           (let [v (<! api-tap)]
             (let [message-uuid (:uuid (meta v))
                   status (second v)]
               (cond
                (and (= uuid message-uuid)
                     (#{:success :failed} status))
                (do (if (= :success status)
                      (success-fn)
                      (failure-fn))
                    ;; There's a chance of a race if the button gets clicked twice.
                    ;; No good ideas on how to fix it, and it shouldn't happen,
                    ;; so punting for now
                    (async/untap api-mult api-tap))

                (nil? v) nil ;; don't recur on closed channel

                :else (recur))))))

(defn append-cycle
  "Adds the button-state to the end of the lifecycle"
  [owner button-state]
  (om/update-state! owner [:lifecycle] #(conj % button-state)))

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

(defn cleanup
  "Cleans up api-tap channel and stops the idle timer from firing"
  [owner]
  (close! (om/get-state owner [:api-tap]))
  (js/clearTimeout (om/get-state owner [:idle-timer])))

(defn stateful-button
(defn stateful-button*
  "Takes an ordinary input or button hiccup form.
  Disables the button while it waits for the API response to come back.
  When the button is clicked, it replaces the button value with data-loading-text,
  when the response comes back, it replaces the button with the data-:status-text for a second."
  [hiccup-form owner]
  (reify
    om/IDisplayName (display-name [_] "Stateful button")
    om/IInitState
    (init-state [_]
      {:lifecycle [:idle]
       ;; use a sliding-buffer so that we don't block
       :api-tap (chan (sliding-buffer 10))
       :idle-timer nil})

    om/IWillUnmount (will-unmount [_] (cleanup owner))

    om/IWillUpdate
    (will-update [_ _ {:keys [lifecycle]}]
      (when (#{:success :failed} (last lifecycle))
        (schedule-idle owner lifecycle)))

    om/IRenderState
    (render-state [_ {:keys [lifecycle api-tap]}]
      (let [api-mult (om/get-shared owner [:comms :api-mult])
            button-state (last lifecycle)
            [tag attrs & rest] hiccup-form
            new-value (get attrs (keyword (str "data-" (name button-state) "-text")) (:value attrs))
            new-body (cond (= :idle button-state) rest
                           (:data-spinner attrs) common/spinner
                           :else new-value)
            new-attrs (-> attrs
                          ;; disable the button when it's not idl
                          (assoc :disabled (not= :idle button-state))
                          (update-in [:class] (fn [c] (cond (= :idle button-state) c
                                                            (string? c) (str c  " disabled")
                                                            (coll? c) (conj c "disabled")
                                                            :else "disabled")))
                          ;; Update the on-click handler to watch the api channel for success
                          (update-in [:on-click] (fn [f]
                                                   (fn [& args]
                                                     (append-cycle owner :loading)
                                                     (let [uuid (utils/uuid)]
                                                       (binding [frontend.async/*uuid* uuid]
                                                         (tap-api api-mult api-tap uuid
                                                                  {:success-fn
                                                                   #(append-cycle owner :success)
                                                                   :error-fn
                                                                   #(append-cycle owner :failed)})
                                                         (apply f args))))))
                          (update-in [:value] (fn [v]
                                                (or new-value v))))]
        (html
         (vec (concat [tag new-attrs]
                      [new-body])))))))

(defn stateful-button
  "Takes an ordinary input or button hiccup form.
   Disables the button while it waits for the API response to come back.
   When the button is clicked, it replaces the button value with data-loading-text,
   when the response comes back, it replaces the button with the data-:status-text for a second."
  [hiccup-form]
  (om/build stateful-button* hiccup-form))
