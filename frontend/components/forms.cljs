(ns frontend.components.forms
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))

(defn tap-api [api-mult uuid success-fn failure-fn]
  (let [api-tap (chan)
        timeout (async/timeout (* 1000 30))]
    (async/tap api-mult api-tap)
    (go-loop []
             (alt! api-tap ([v]
                              (cond
                               (and (= uuid (:uuid (meta v)))
                                    (#{:success :failed} (second v)))
                               (do (if (= :success (second v))
                                     (success-fn)
                                     (failure-fn))
                                   (close! api-tap))

                               (nil? v) nil ;; don't recur on closed channel

                               :else (recur)))

                   timeout (do (close! api-tap)
                               (failure-fn)
                               (utils/mlog "Gave up on api-ch"))))))

(defn append-cycle [owner lifecycle-value]
  (om/update-state! owner [:lifecycle] (fn [l] (conj l lifecycle-value))))

(defn schedule-idle [owner lifecycle]
  (let [cycle-count (count lifecycle)]
    (js/setTimeout #(om/update-state! owner [:lifecycle]
                                      (fn [cycles]
                                        (if (= (count cycles) cycle-count)
                                          (conj cycles :idle)
                                          cycles)))
                   1000)))

(defn stateful-submit [hiccup-form owner]
  (reify
    om/IInitState
    (init-state [_]
      {:lifecycle [:idle]})
    om/IRenderState
    (render-state [_ {:keys [lifecycle]}]
      (utils/inspect lifecycle)
      (when (#{:success :failed} (last lifecycle))
        (schedule-idle owner lifecycle))
      (let [api-mult (om/get-shared owner [:comms :api-mult])
            button-state (last lifecycle)
            [tag attrs & rest] hiccup-form
            new-value (get attrs (keyword (str "data-" (name button-state) "-text")))
            new-attrs (-> attrs
                          (assoc :disabled (= :loading button-state))
                          (update-in [:on-click] (fn [f]
                                                   (fn [& args]
                                                     (append-cycle owner :loading)
                                                     (let [uuid (utils/uuid)]
                                                       (binding [frontend.async/*uuid* uuid]
                                                         (tap-api api-mult uuid
                                                                  #(append-cycle owner :success)
                                                                  #(append-cycle owner :failed))
                                                         (apply f args))))))
                          (update-in [:value] (fn [v]
                                                (or new-value v))))]
        (html
         (vec (concat [tag new-attrs]
                      (if new-value [new-value] rest))))))))
