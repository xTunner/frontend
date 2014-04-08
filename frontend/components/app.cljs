(ns frontend.components.app
  (:require [ankha.core :as ankha]
            [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.components.build :as build-com]
            [frontend.components.dashboard :as dashboard]
            [frontend.components.footer :as footer]
            [frontend.components.key-queue :as keyq]
            [frontend.components.navbar :as navbar]
            [frontend.components.placeholder :as placeholder]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(def keymap
  (atom nil))

(defn dominant-component [app-state]
  (if (get-in app-state [:inspected-project :build-num])
    build-com/build
    dashboard/dashboard))

(defn app [app owner opts]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch (get-in opts [:comms :controls])
            persist-state! #(put! controls-ch [:state-persisted])
            restore-state! #(put! controls-ch [:state-restored])
            dom-com (dominant-component app)]
        (reset! keymap {["ctrl+s"] persist-state!
                        ["ctrl+r"] restore-state!})
        (html/html
         [:div.inner
          (om/build keyq/KeyboardHandler app
                    {:opts {:keymap keymap
                            :error-ch (get-in app [:comms :errors])}})
          [:header
           (om/build navbar/navbar app {:opts opts})]
          [:main
           (om/build dom-com app {:opts opts})]
          [:footer
           (footer/footer)]])))))
