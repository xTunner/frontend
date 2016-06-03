(ns frontend.components.admin
  (:require [ankha.core :as ankha]
            [inflections.core :refer [pluralize]]
            [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as str]
            [frontend.async :refer [raise! navigate!]]
            [frontend.components.builds-table :as builds-table]
            [frontend.components.common :as common]
            [frontend.components.pieces.tabs :as tabs]
            [frontend.components.shared :as shared]
            [frontend.datetime :as datetime]
            [frontend.routes :as routes]
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
           [:button.btn.btn-primary {:value "Switch user"
                                     :type "submit"
                                     :on-click (fn [event]
                                                 ;; a higher level handler will stop all form submissions
                                                 ;;
                                                 ;; see frontend.components.app/app*
                                                 (.stopPropagation event))}
            "Switch user"]]]]]))))

(defn current-seat-usage [active-users total-seats]
  [:span
   "There " (if (= 1 active-users) "is" "are") " currently "
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

         [:p (current-seat-usage (get-in app (conj state/license-path :seat_usage))
                                 (get-in app (conj state/license-path :seats)))
          " You can deactivate users in "
          [:a {:href "/admin/users"} "user settings."]]]))))

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
            (om/build tabs/tab-row {:tabs [{:name :builders
                                            :label "Builders"}
                                           {:name :running-builds
                                            :label "Running Builds"}
                                           {:name :queued-builds
                                            :label "Queued Builds"}]
                                    :selected-tab-name current-tab
                                    :on-tab-click #(navigate! owner (routes/v1-admin-fleet-state-path {:_fragment (name %)}))})
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

(defn user [{:keys [user current-user]} owner]
  (let [scope-labels {"write-settings" "Admin"
                      "read-settings" "Read-only Admin"
                      "none" "Normal"}]
    (reify
      om/IRender
      (render [_]
        (let [show-suspend-unsuspend? (and (#{"all" "write-settings"} (:admin current-user))
                                           (not= (:login current-user) (:login user)))
              scope (-> user :admin_scopes relevant-scope)
              dropdown-options (cond->> (keys scope-labels)
                                        (not= "read-settings" scope) (remove #{"read-settings"}))]
          (html
            [:tr
             [:td (:login user)]
             [:td (:name user)]
             [:td
              [:div.form-inline
               ;; Admin toggles
               (if show-suspend-unsuspend?
                 [:select.form-control
                  {:on-change #(raise! owner [:set-admin-scope
                                              {:login (:login user)
                                               :scope (-> % .-target .-value keyword)}])
                   :value scope}
                  (for [opt dropdown-options]
                    [:option {:value opt} (scope-labels opt)])]
                 (-> user :admin_scopes relevant-scope scope-labels))
               ;; Suspend/unsuspend toggles
               (when show-suspend-unsuspend?
                 (let [action (if (:suspended user) :unsuspend-user :suspend-user)]
                   [:button.secondary
                    {:style {:margin-left "1em"}
                     :on-click #(raise! owner [action (select-keys user [:login])])}
                    (case action
                      :suspend-user "Suspend"
                      :unsuspend-user "Activate")]))]]]))))))

(defn users [app owner]
  (reify
    om/IDisplayName (display-name [_] "User Admin")

    om/IRender
    (render [_]
      (let [all-users (:all-users app)
            active-users (filter #(and (pos? (:sign_in_count %))
                                       (not (:suspended %)))
                                 all-users)
            suspended-users (filter #(and (pos? (:sign_in_count %))
                                          (:suspended %))
                                    all-users)
            suspended-new-users (filter #(and (zero? (:sign_in_count %))
                                              (:suspended %))
                                        all-users)
            inactive-users (filter #(and (zero? (:sign_in_count %))
                                         (not (:suspended %)))
                                   all-users)
            current-user (:current-user app)
            num-licensed-users (get-in app (conj state/license-path :seats))
            num-active-users (get-in app (conj state/license-path :seat_usage))
            table-header [:thead.head
                          [:tr
                           [:th.github-id "GitHub ID"]
                           [:th.name "Name"]
                           [:th.permissions "Permissions"]]]]
        (html
          [:div.users {:style {:padding-left "10px"}}
           [:h1 "Users"]

           [:div.card.detailed
            [:h3 "Active"]
            [:div.details (current-seat-usage num-active-users num-licensed-users)]
            (when (not-empty active-users)
              [:table
               table-header
               [:tbody.body (om/build-all user (mapv (fn [u] {:user u
                                                              :current-user current-user})
                                                     active-users))]])]

           [:div.card.detailed
            [:h3 "Suspended"]
            [:div.details "Suspended users are prevented from logging in and do not count towards the number your license allows."]
            (when (not-empty suspended-users)
              [:table
               table-header
               [:tbody.body (om/build-all user (mapv (fn [u] {:user u
                                                              :current-user current-user})
                                                     suspended-users))]])]

           ;;Don't show this section if there are no suspended new users to show
           (when (not-empty suspended-new-users)
             [:div.card.detailed
              [:h3 "Suspended New Users"]
              [:div.details "Suspended new users require an admin to unsuspend them before they can log on and do not count towards the number your license allows."]
              [:table
               table-header
               [:tbody.body (om/build-all user (mapv (fn [u] {:user u
                                                              :current-user current-user})
                                                     suspended-new-users))]]])

           [:div.card.detailed
            [:h3 "Inactive Users"]
            [:div.details "Inactive users have never logged on and also do not count towards your license limits."]
            (when (not-empty inactive-users)
              [:table
               table-header
               [:tbody.body (om/build-all user (mapv (fn [u] {:user u
                                                              :current-user current-user})
                                                     inactive-users))]])]])))))

(defn boolean-setting-entry [item owner]
  (reify
    om/IRender
    (render [_]
      (html [:div.details
             [:div.btn-group
              [:button.btn.btn-default
               (if-let [value (:value item)]
                 {:class "active"}
                 {:on-click #(raise! owner [:system-setting-changed
                                            (assoc item :value true)])})
               "true"]
              [:button.btn.btn-default
               (if-let [value (:value item)]
                 {:on-click #(raise! owner [:system-setting-changed
                                            (assoc item :value false)])}
                 {:class "active"})
               "false"]]
             (when (:updating item)
               [:div.loading-spinner common/spinner])]))))

(defn number-setting-entry [item owner]
  (reify
    om/IRender
    (render [_]
      (let [field-ref (str (:name item) "-input")
            get-field-value #(some->> field-ref
                                      (om/get-node owner)
                                      .-value
                                      js/Number)]
        (html
         [:div.form-group.details
          [:input.form-control
           {:type "text"
            :default-value (:value item)
            :ref field-ref}]
          (when-let [error-message (:error item)]
            (om/build common/flashes (str error-message ". ")))
          [:button.btn.btn-primary
           {:on-click #(raise! owner [:system-setting-changed
                                      (assoc item
                                             :value (get-field-value))])}
           (if-not (:updating item)
                   "Save"
                   common/spinner)]])))))

(defn system-setting [item owner]
  (reify
    om/IRender
    (render [_]
      (html [:div.card.detailed
             [:h4 (:human_name item)]
             [:div.details (:description item)]
             (om/build (case (:value_type item)
                         "Boolean" boolean-setting-entry
                         "Number" number-setting-entry)
                       item)]))))

(defn system-settings [app owner]
  (reify
    om/IRender
    (render [_]
      (html [:div
             [:h1 "System Settings"]
             [:p "Low level settings for tweaking the behavior of the system."]
             [:div (om/build-all system-setting
                                 (get-in app state/system-settings-path))]]))))

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
              :system-settings (om/build system-settings app)
              (om/build overview app))]]])))))
