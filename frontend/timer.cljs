(ns frontend.timer
  (:require [om.core :as om :include-macros true]))

(defn initialize
  "Sets up an atom that will keep track of components that should refresh when
  the current time changes."
  []
  (let [components (atom #{})]
    (js/setInterval #(doseq [component @components] (om/refresh! component)) 1000)
    components))

(defn set-updating!
  "Registers or unregisters a component to be refreshed every time a global
  timer ticks.  A component must be un-registered when disposing a component
  via IWillUnmount."
  [owner enabled?]
  (swap! (om/get-shared owner [:timer-atom])
         (if enabled? conj disj)
         owner))
