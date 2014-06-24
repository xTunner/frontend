(ns frontend.components.app
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [frontend.components.build :as build-com]
            [frontend.components.dashboard :as dashboard]
            [frontend.components.add-projects :as add-projects]
            [frontend.components.footer :as footer]
            [frontend.components.inspector :as inspector]
            [frontend.components.key-queue :as keyq]
            [frontend.components.navbar :as navbar]
            [frontend.components.placeholder :as placeholder]
            [frontend.components.project-settings :as project-settings]
            [frontend.components.landing :as landing]
            [frontend.components.common :as common]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [ankha.core :as ankha]
            [sablono.core :as html :refer-macros [html]]))

(def keymap
  (atom nil))

(defn loading [app owner]
  (reify
    om/IRender
    (render [_] (html [:div.loading-spinner common/spinner]))))

(defn dominant-component [app-state]
  (condp = (get-in app-state [:navigation-point])
    :build build-com/build
    :dashboard dashboard/dashboard
    :add-projects add-projects/add-projects
    :loading loading
    :project-settings project-settings/project-settings))
    :project-settings project-settings/project-settings
    :landing landing/home

(defn app [app owner]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch (om/get-shared owner [:comms :controls])
            persist-state! #(put! controls-ch [:state-persisted])
            restore-state! #(put! controls-ch [:state-restored])
            dom-com (dominant-component app)]
        (reset! keymap {["ctrl+s"] persist-state!
                        ["ctrl+r"] restore-state!})
        (html/html
         [:div#app {:class (if (:current-user app) "inner" "outer")}
          (om/build keyq/KeyboardHandler app
                    {:opts {:keymap keymap
                            :error-ch (get-in app [:comms :errors])}})
          (when (:inspector? utils/initial-query-map)
            ;; XXX inspector still needs lots of work. It's slow and it defaults to
            ;;     expanding all datastructures.
            (om/build inspector/inspector app))
          [:main.app-main
           [:header.main-head
            (om/build navbar/navbar app)]
           [:div.main-body
            (om/build dom-com app)]
           [:footer.main-foot
            (footer/footer)]]])))))

