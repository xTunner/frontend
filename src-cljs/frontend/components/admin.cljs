(ns frontend.components.admin
  (:require [ankha.core :as ankha]
            [inflections.core :refer [pluralize]]
            [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [raise!]]
            [frontend.components.builds-table :as builds-table]
            [frontend.components.common :as common]
            [frontend.components.shared :as shared]
            [frontend.datetime :as datetime]
            [frontend.state :as state]
            [frontend.stefon :as stefon]
            [frontend.utils :as utils :include-macros true]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [html]]))

(defn build-state [app owner]
  (reify
    om/IDisplayName (display-name [_] "Admin Build State")
    om/IRender
    (render [_]
      (let [build-state (get-in app state/build-state-path)]
        (html
         [:section {:style {:padding-left "10px"}}
          [:a {:href "/api/v1/admin/build-state" :target "_blank"} "View raw"]
          " / "
          [:a {:on-click #(raise! owner [:refresh-admin-build-state-clicked])} "Refresh"]
          (if-not build-state
            [:div.loading-spinner common/spinner]
            [:code (om/build ankha/inspector build-state)])])))))

(defn switch [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div.container-fluid
        [:div.row-fluid
         [:div.span9
          [:p "Switch user"]
          [:form.form-inline {:method "post", :action "/admin/switch-user"}
           [:input.input-medium {:name "login", :type "text"}]
           [:input {:value (utils/csrf-token)
                    :name "CSRFToken",
                    :type "hidden"}]
           [:button.btn.btn-primary {:value "Switch user", :type "submit"}
            "Switch user"]]]]]))))

(defn current-seat-usage-p
  [active-users total-seats]
  [:p "There " (if (= 1 active-users) "is" "are" ) " currently "
   [:b (pluralize active-users "active user")]
   " out of "
   [:b (pluralize total-seats "licensed user")]
   "."])

(defn overview [app owner]
  (reify
    om/IDisplayName (display-name [_] "Admin Build State")
    om/IRender
    (render [_]
      (html
        [:section {:style {:padding-left "10px"}}
         [:h1 "CircleCI Version Info"]
         [:p
          "You are running "
          [:b
           "CircleCI "
           (if-let [enterprise-version (get-in app [:render-context :enterprise_version])]
             (list
               "Enterprise "
               enterprise-version)
             (list
               "in "
               (:environment app)))]
          "."]

         (conj (current-seat-usage-p (get-in app (conj state/license-path :seat_usage))
                                     (get-in app (conj state/license-path :seats)))
               " You can deactivate users in "
               [:a {:href "/admin/users"} "user settings."])]))))

(defn builders [builders owner]
  (reify
    om/IDisplayName (display-name [_] "Admin Build State")
    om/IRender
    (render [_]
      (html
        [:section {:style {:padding-left "0"}}
         [:header {:style {:padding-top "10px"}}
          [:a {:href "/api/v1/admin/build-state-summary" :target "_blank"} "View raw"]
          " / "
          [:a {:on-click #(raise! owner [:refresh-admin-fleet-state-clicked])} "Refresh"]]
         (if-not builders
           [:div.loading-spinner common/spinner]
           ;; FIXME: This table shouldn't really be .recent-builds-table; it's
           ;; a hack to steal a bit of styling from the builds table until we
           ;; properly address the styling for this table and admin tools in
           ;; general.
           [:table.recent-builds-table
            [:thead
             [:tr
              [:th "Instance ID"]
              [:th "Instance Type"]
              [:th "Boot Time"]
              [:th "Busy Containers"]
              [:th "State"]]]
            [:tbody
             (if (seq builders)
               (for [instance builders]
                 [:tr
                  [:td (:instance_id instance)]
                  [:td (:ec2_instance_type instance)]
                  [:td (datetime/long-datetime (:boot_time instance))]
                  [:td (:busy instance) " / " (:total instance)]
                  [:td (:state instance)]])
               [:tr
                [:td "No available masters"]])]])]))))

(defn admin-builds-table [builds owner {:keys [tab]}]
  (reify
    om/IRender
    (render [_]
      (html
        [:div
         [:header {:style {:padding-top "10px" :padding-bottom "10px"}}
          [:a
           {:href (case tab
                    :running-builds "/admin/running-builds"
                    :queued-builds "/admin/queued-builds")}
           "See more"]
          " / "
          [:a {:on-click #(raise! owner [:refresh-admin-build-list {:tab tab}])} "Refresh"]
          (if (nil? builds)
            [:div.loading-spinner common/spinner])]
         (om/build builds-table/builds-table builds
                   {:opts {:show-actions? true
                           :show-parallelism? true
                           :show-branch? false
                           :show-log? false}})]))))

(defn fleet-state [app owner]
  (reify
    om/IRender
    (render [_]
      (let [fleet-state (->> (get-in app state/fleet-state-path)
                             (filter #(-> % :boot_time not-empty)) ;; remove corrupt entries
                             (remove #(-> % :builder_tags (= ["os:none"])))
                             (sort-by :instance_id))
            summary-counts (get-in app state/build-system-summary-path)
            current-tab (or (get-in app [:navigation-data :tab]) :builders)]
        (html
          [:div {:style {:padding-left "10px"}}
           [:h1 "Fleet State"]
           [:div
            (if-not summary-counts
              [:div.loading-spinner common/spinner]
              (let [container-totals (->> fleet-state
                                          (map #(select-keys % [:free :busy :total]))
                                          (apply merge-with +))
                    queued-builds (+ (get-in summary-counts [:usage_queue :builds])
                                     (get-in summary-counts [:run_queue :builds]))
                    queue-container-count (+ (get-in summary-counts [:usage_queue :containers])
                                             (get-in summary-counts [:run_queue :containers]))]
                [:div
                 [:div "capacity"
                  [:ul [:li "total containers: " (:total container-totals)]]]
                 [:div "in use"
                  [:ul
                   [:li "running builds: " (get-in summary-counts [:running :builds])]
                   [:li "containers in use: " (:busy container-totals)]]]
                 [:div "queued"
                  [:ul
                   [:li "queued builds: " queued-builds]
                   [:li "containers requested by queued builds: " queue-container-count]]]]))
            [:ul.nav.nav-tabs
             (for [[tab-key tab-name] {:builders "Builders"
                                       :running-builds "Running Builds"
                                       :queued-builds "Queued Builds"}]
               [:li (when (= current-tab tab-key) {:class "active"})
                [:a {:href (str "#" (name tab-key))} tab-name]])]
            (if (#{:running-builds :queued-builds} current-tab)
              (om/build admin-builds-table
                        (:recent-builds app)
                        {:opts {:tab current-tab}})
              (om/build builders fleet-state))]])))))

(defn license [app owner]
  (reify
    om/IDisplayName (display-name [_] "License Info")
    om/IRender
    (render [_]
      (html
        [:section {:style {:padding-left "10px"}}
         [:h1 "License Info"]
         (let [license (get-in app state/license-path)]
           (if-not license
             [:div.loading-spinner common/spinner]
             (list
              [:p "License Type: " [:b (:type license)]]
              [:p "License Status: Term (" [:b (:expiry_status license)] "), Seats ("
               [:b (:seat_status license)] ": "
               (get-in app (conj state/license-path :seat_usage)) "/"
               (get-in app (conj state/license-path :seats)) ")"]
              [:p "Expiry date: " [:b (datetime/medium-date (:expiry_date license))]])))]))))

(defn relevant-scope
  [admin-scopes]
  (or (some (set admin-scopes) ["write-settings" "read-settings"])
    "none"))

(defn admin-in-words
  [admin-scopes]
  (case (relevant-scope admin-scopes)
    "write-settings" "admin"
    "read-settings" "read-only admin"
    "none"))

(defn user [{:keys [user admin-write-scope?]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:li
         (:login user)
         " "
         (when admin-write-scope?
           (let [action (if (:suspended user) :unsuspend-user :suspend-user)]
             [:button.btn.btn-xs.btn-default
              {:on-click #(raise! owner [action (select-keys user [:login])])}
              (case action
                :suspend-user "suspend"
                :unsuspend-user "reactivate")]))
         " "
         (let [relevant-scope (-> user :admin_scopes relevant-scope)]
           (if admin-write-scope?
             [:div.btn-group.btn-group-xs
              [:button.btn.btn-default
               (if (= "write-settings" relevant-scope)
                 {:class "active"}
                 {:on-click #(raise! owner [:set-admin-scope
                                            {:login (:login user)
                                             :scope :write-settings}])})
               "admin"]
              ;; read-settings generally hidden for until we sort out what we
              ;; really want the scope precision to be
              (when (= "read-settings" relevant-scope)
                [:button.btn.btn-default.active "read-only admin"])
              [:button.btn.btn-default
               (if (= "none" relevant-scope)
                 {:class "active"}
                 {:on-click #(raise! owner [:set-admin-scope
                                            {:login (:login user)
                                             :scope :none}])})
               "none"]]
             (-> user :admin_scopes admin-in-words)))]))))

(defn users [app owner]
  (reify
    om/IDisplayName (display-name [_] "User Admin")

    om/IRender
    (render [_]
      (let [all-users (:all-users app)
            active-users (filter #(and (not= 0 (:login-count %))
                                       (not (:suspended %)))
                                 all-users)
            suspended-users (filter :suspended all-users)
            admin-write-scope? (#{"all" "write-settings"}
                                (get-in app [:current-user :admin]))
            num-licensed-users (get-in app (conj state/license-path :seats))
            num-active-users (get-in app (conj state/license-path :seat_usage))]
        (html
         [:section {:style {:padding-left "10px"}}
          [:h1 "Users"]

          (current-seat-usage-p num-active-users num-licensed-users)

          [:p "Suspended users are prevented from logging in and do not count towards the number your license allows."]

          [:h2 "active"]
           [:ul (om/build-all user (mapv (fn [u] {:user u
                                                  :admin-write-scope? admin-write-scope?})
                                        active-users))]

         [:h2 "suspended"]
           [:ul (om/build-all user (mapv (fn [u] {:user u
                                                  :admin-write-scope? admin-write-scope?})
                                         suspended-users))]])))))

(defn admin-settings [app owner]
  (reify
    om/IRender
    (render [_]
      (let [subpage (:admin-settings-subpage app)]
        (html
         [:div#admin-settings
            [:div.admin-settings-inner
             [:div#subpage
              (case subpage
                :fleet-state (om/build fleet-state app)
                :license (om/build license app)
                :users (om/build users app)
                (om/build overview app))]]])))))
