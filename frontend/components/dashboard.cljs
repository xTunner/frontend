(ns frontend.components.dashboard
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [frontend.async :refer [put!]]
            [frontend.components.builds-table :as builds-table]
            [frontend.components.common :as common]
            [frontend.routes :as routes]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn dashboard [data owner]
  (reify
    om/IRender
    (render [_]
      (let [builds (:recent-builds data)
            nav-ch (get-in data [:comms :nav])
            controls-ch (om/get-shared owner [:comms :controls])
            nav-data (:navigation-data data)
            page (js/parseInt (get-in nav-data [:query-params :page] 0))
            builds-per-page (:builds-per-page data)]
        (html
         ;; XXX logic for dashboard not ready
         ;; XXX logic for show add projects
         ;; XXX logic for trial notices
         ;; XXX logic for show_build_table
         [:div#dashboard
          [:section
           (if-not builds
             [:div.loading-spinner common/spinner]
             (list (om/build builds-table/builds-table builds {:opts {:show-actions? false}})
                   [:div.recent-builds-pager
                    [:a
                     {:href (routes/v1-dashboard-path (assoc nav-data :page (max 0 (dec page))))
                      ;; no newer builds if you're on the first page
                      :class (when (zero? page) "disabled")}
                     [:i.fa.fa-long-arrow-left]
                     [:span " Newer builds"]]
                    [:a
                     {:href (routes/v1-dashboard-path (assoc nav-data :page (inc page)))
                      ;; no older builds if you have less builds on the page than an
                      ;; API call returns
                      :class (when (> builds-per-page (count builds)) "disabled")}
                     [:span "Older builds "]
                     [:i.fa.fa-long-arrow-right]]]))]])))))
