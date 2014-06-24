(ns frontend.components.account
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as string]
            [frontend.async :refer [put!]]
            [frontend.components.common :as common]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom]
            [sablono.core :as html :refer-macros [html]]))

(defn active-class-if-active [current-subpage subpage-link]
  (if (= current-subpage subpage-link)
    {:class "active"}))

(defn handle-email-notification-change [ch pref]
  (put! ch [:preferences-updated {:basic_email_prefs pref}]))

(defn notifications [app owner]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch (om/get-shared owner [:comms :controls])
            email-pref  (get-in app (conj state/user-path :basic_email_prefs))]
        (html/html
         [:div#settings-notification
          [:div.notification-item
           [:form
            [:fieldset
             [:legend "Email notifications"]
             [:label.radio
              [:input
               {:name "email_pref",
                :type "radio"
                :checked (= email-pref "all")
                :on-change (partial handle-email-notification-change controls-ch "all")}]
              [:span
               "Send me a personalized email for every build in all of my projects."]]
             [:label.radio
              [:input
               {:name "email_pref",
                :type "radio"
                :checked (= email-pref "smart")
                :on-change (partial handle-email-notification-change controls-ch "smart")}]
              [:span
               "Send me a personalized email every time a build on a branch I've pushed to fails; also once they're fixed."]]
             [:label.radio
              [:input
               {:name "email_pref",
                :type "radio"
                :checked (= email-pref "none")
                :on-change (partial handle-email-notification-change controls-ch "none")}]
              [:span "Don't send me emails."]]]]]
          [:div.notification-item
           [:form#email_address.form-horizontal
            [:fieldset
             [:legend
              "Email Addresses"
              [:i.fa.fa-info-circle
               {:data-bind
                "tooltip: {title: \\Addresses added to your GitHub account will be reflected here\\, placement: 'right', trigger: 'hover'}"}]]
             [:div
              {:data-bind "foreach: all_emails"}
              (map (fn [email]
                     [:label.radio
                      [:input
                       {:checked (= (get-in app (conj state/user-path :selected_email)) email)
                        :value email
                        :name "selected_email"
                        :type "radio"
                        :on-click (fn [event]
                                    (put! controls-ch [:preferences-updated {:selected_email email}]))}]
                      [:span email]]) (get-in app (conj state/user-path :all_emails)))]]]]
          [:div.notification-item
           [:form
            [:fieldset
             [:legend "Project preferences"]
             [:p
              "Projects can be individually configured, from a project's 'Settings' page. Instant message settings are per-project; edit a project to set them."]]]]])))))

(defn account [app owner]
  (reify
    om/IRender
    (render [_]
      (let [subpage     (get-in app state/account-subpage-path)
            coms        {:notifications notifications}
            subpage-com (get coms subpage)]
        (html
         [:div#account-settings
          [:div.account-top
           [:ul.nav.nav-tabs
            [:li#notifications (active-class-if-active subpage :notifications)
             [:a {:href (routes/v1-account-subpage {:subpage "notifications"})}
              "Notifications"]]
            [:li#api (active-class-if-active subpage :api)
             [:a {:href (routes/v1-account-subpage {:subpage "api"})} "API Tokens"]]
            [:li#heroku (active-class-if-active subpage :heroku)
             [:a {:href (routes/v1-account-subpage {:subpage "heroku"})} "Heroku Key"]]
            [:li#plans (active-class-if-active subpage :plans)
             [:a {:href (routes/v1-account-subpage {:subpage "plans"})} "Plan Pricing"]]]]
          [:div.row (common/flashes)]
          [:div.settings-item
           {:data-bind "with: current_user"}
           [:div.settings-item-inner
            [:div#subpage
             (when subpage-com
               (om/build subpage-com app))]]]])))))
