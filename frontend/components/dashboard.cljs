(ns frontend.components.dashboard
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer put! close!]]
            [frontend.components.sidebar :as sidebar]
            [frontend.components.builds-table :as builds-table]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn dashboard [data owner opts]
  (reify
    om/IRender
    (render [_]
      (let [builds (:recent-builds data)
            nav-ch (get-in data [:comms :nav])
            controls-ch (get-in data [:comms :controls])]
        (html
         ;; XXX logic for dashboard not ready
         ;; XXX logic for show add projects
         ;; XXX logic for trial notices
         ;; XXX logic for show_build_table
         [:div#dashboard
          (om/build sidebar/sidebar
                    {:current-user (:current-user data)
                     :projects (:projects data)
                     :settings (:settings data)}
                    {:opts opts})
          [:section
           (om/build builds-table/builds-table builds {:opts opts})]])))))
