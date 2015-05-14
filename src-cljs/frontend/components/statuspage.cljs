(ns frontend.components.statuspage
  (:require [cljs.core.async :as async :refer [<!]]
            [om.core :as om :include-macros true]
            [frontend.async :refer [raise!]]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.ajax :as ajax])
  (:require-macros [cljs.core.async.macros :as am :refer [go]]
                   [frontend.utils :refer [html]]))

;; example data from https://manage.statuspage.io/pages/6w4r0ttlx5ft/status-widget
(def example-summary-json
  {:page {:id "6w4r0ttlx5ft", :name "CircleCI", :url "http://status.circleci.com", :updated_at "2015-05-13T01:01:48-07:00"}, :status {:description "Partial System Outage", :indicator "major"}, :components [{:created_at "2014-05-03T01:22:07.274Z", :description nil, :id "b13yz5g2cw10", :name "API", :page_id "6w4r0ttlx5ft", :position 1, :status "partial_outage", :updated_at "2014-05-14T20:34:43.340Z"} {:created_at "2014-05-03T01:22:07.286Z", :description nil, :id "9397cnvk62zn", :name "Management Portal", :page_id "6w4r0ttlx5ft", :position 2, :status "major_outage", :updated_at "2014-05-14T20:34:44.470Z"}], :incidents [{:page_id "6w4r0ttlx5ft", :incident_updates [{:body "Our master database has ham sandwiches flying out of the rack, and we're working our hardest to stop the bleeding. The whole site is down while we restore functionality, and we'll provide another update within 30 minutes.", :created_at "2014-05-14T14:22:40.301-06:00", :display_at "2014-05-14T14:22:40.301-06:00", :id "jdy3tw5mt5r5", :incident_id "cp306tmzcl0y", :status "identified", :updated_at "2014-05-14T14:22:40.301-06:00"}], :resolved_at nil, :name "Unplanned Database Outage", :shortlink "http://stspg.dev:5000/Q0E", :updated_at "2014-05-14T14:35:21.711-06:00", :status "identified", :id "cp306tmzcl0y", :monitoring_at nil, :impact "critical", :created_at "2014-05-14T14:22:39.441-06:00"}], :scheduled_maintenances [{:page_id "6w4r0ttlx5ft", :incident_updates [{:body "Our data center has informed us that they will be performing routine network maintenance. No interruption in service is expected. Any issues during this maintenance should be directed to our support center", :created_at "2014-05-14T14:24:41.913-06:00", :display_at "2014-05-14T14:24:41.913-06:00", :id "qq0vx910b3qj", :incident_id "w1zdr745wmfy", :status "scheduled", :updated_at "2014-05-14T14:24:41.913-06:00"}], :resolved_at nil, :name "Network Maintenance (No Interruption Expected)", :scheduled_for "2014-05-17T22:00:00.000-06:00", :shortlink "http://stspg.dev:5000/Q0F", :scheduled_until "2014-05-17T23:30:00.000-06:00", :updated_at "2014-05-14T14:24:41.918-06:00", :status "scheduled", :id "w1zdr745wmfy", :monitoring_at nil, :impact "none", :created_at "2014-05-14T14:24:40.430-06:00"} {:page_id "6w4r0ttlx5ft", :incident_updates [{:body "Scheduled maintenance is currently in progress. We will provide updates as necessary.", :created_at "2014-05-14T14:34:20.036-06:00", :display_at "2014-05-14T14:34:20.036-06:00", :id "drs62w8df6fs", :incident_id "k7mf5z1gz05c", :status "in_progress", :updated_at "2014-05-14T14:34:20.036-06:00"} {:body "We will be performing rolling upgrades to our web tier with a new kernel version so that Heartbleed will stop making us lose sleep at night. Increased load and latency is expected, but the app should still function appropriately. We will provide updates every 30 minutes with progress of the reboots.", :created_at "2014-05-14T14:27:18.845-06:00", :display_at "2014-05-14T14:27:18.845-06:00", :id "z40y7398jqxc", :incident_id "k7mf5z1gz05c", :status "scheduled", :updated_at "2014-05-14T14:27:18.845-06:00"}], :resolved_at nil, :name "Web Tier Recycle", :scheduled_for "2014-05-14T14:30:00.000-06:00", :shortlink "http://stspg.dev:5000/Q0G", :scheduled_until "2014-05-14T16:30:00.000-06:00", :updated_at "2014-05-14T14:35:12.258-06:00", :status "in_progress", :id "k7mf5z1gz05c", :monitoring_at nil, :impact "minor", :created_at "2014-05-14T14:27:17.303-06:00"}]})

(defn update-statuspage-state [component]
  (if true
    (om/set-state! component :statuspage {:summary example-summary-json})
    (go
     (let [res (<! (ajax/managed-ajax :get "https://6w4r0ttlx5ft.statuspage.io/api/v2/summary.json"))]
       (when (= :success (:status res))
         (om/set-state! component :statuspage {:summary (->> res :resp)}))))))

(defn statuspage [app owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (let [delay-ms (* 30 1000)
            interval-id (js/setInterval #(update-statuspage-state owner)
                                        delay-ms)]
        (om/set-state! owner :statuspage-interval-id interval-id)
        ;; call it once up front rather than wait the whole delay
        ;; period for first call
        (update-statuspage-state owner)))

    om/IWillUnmount
    (will-unmount [_]
      (js/clearInterval (om/get-state owner :statuspage-interval-id)))

    om/IRender
    (render [_]
      (let [summary-response (:summary (om/get-state owner :statuspage))
            ind (get-in summary-response [:status :indicator])
            desc (get-in summary-response [:status :description])]
        (html [:div#statuspage-bar {:class (if (= ind "none") "statusok" "statusnotok")}
               [:p (str "status: " desc)
                [:ul (for [incident (:incidents summary-response)]
                       (let [updates (:incident_updates incident)]
                         [:p (str (:impact incident) " incident created at " (:created_at incident)
                                  (when (not (nil? updates))
                                    (str " - updated at " (:updated_at (first updates)) ", " (:body (first updates)))))]))]]])))))
