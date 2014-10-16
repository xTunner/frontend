(ns frontend.scroll
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [frontend.utils :as utils :include-macros true]
            [goog.events])
  (:require-macros [cljs.core.async.macros :as am :refer [go go-loop alt!]]))


(def scroller-state (atom {:event-key nil
                           :callbacks {}}))

(def scroller-ch (chan (sliding-buffer 1)))

(defn setup-scroll-handler []
  (go-loop []
    (when-let [event (<! scroller-ch)]
      (doseq [[key callback] (:callbacks @scroller-state)]
        (utils/swallow-errors (callback event)))
      (recur))))

(defn register-fn [id cb]
  (swap! scroller-state (fn [state]
                          (-> state
                              (merge
                               (when-not (:event-key state)
                                 {:event-key (goog.events/listen
                                              js/window
                                              "scroll"
                                              #(put! scroller-ch %))}))
                              (assoc-in [:callbacks id] cb)))))

(defn deregister-fn [id]
  (swap! scroller-state (fn [state]
                          (if (-> state :callbacks (dissoc id) empty?)
                            (do
                              (goog.events/unlistenByKey (:event-key state))
                              {:event-key nil
                               :callbacks {}})
                            (update-in state [:callbacks] dissoc id)))))
