(ns frontend.components.account
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as string]
            [frontend.async :refer [put!]]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.datetime :as datetime]
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

;; XXX 1. Styling is off here (spacing between avatar and login name)
(defn plans [app owner]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch   (om/get-shared owner [:comms :controls])
            user-and-orgs (conj (get-in app state/user-organizations-path)
                                (get-in app state/user-path))]
        (html/html
         [:div#settings-plans
          [:div.plans-item [:h2 "Org Settings"]]
          [:div.plans-item
           [:h4 "Set up a plan for one of your Organizations:"]
           [:p "You can set up plans for any organization that you admin."]
           [:div
            (map
             (fn [org]
               (let [org-url    (routes/v1-org-settings-subpage {:org     (:login org)
                                                                 :subpage "plans"})
                     ;; XXX Is there a nicer helper for sized avatar urls?
                     avatar-url (if-let [avatar-url (:avatar_url org)]
                                  (str avatar-url "25")
                                  (gh-utils/gravatar-url {:gravatar_id (:gravatar_id org)
                                                          :login       (:login org)
                                                          :size        25}))]
                 [:div
                  [:a
                   {:href org-url}
                   [:img
                    {:src    avatar-url
                     :height 25
                     :width  25}]]
                  [:a
                   {:href org-url}
                   [:span (:login org)]]
                  [:br]
                  [:br]]))
             user-and-orgs)]]])))))

;; XXX 1. The placeholder drops over the field value whenever saving, not
;; sure why
;; XXX 2. How can we re-enable the form after it's been processed?
(defn heroku-key [app owner]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch          (om/get-shared owner [:comms :controls])
            heroku-api-key       (get-in app (conj state/user-path :heroku_api_key))
            heroku-api-key-input (str (om/get-state owner :heroku-api-key-input))
            processing-form?     (om/get-state owner :processing?)
            form-processed?      false
            submit-form!         #(when (seq heroku-api-key-input)
                                    (om/set-state! owner :processing? true)
                                    (put! controls-ch [:heroku-key-add-attempted {:heroku_api_key %}]))]
        (html/html
         [:div#settings-heroku
          [:div.heroku-item
           [:h2 "Heroku API key"]
           [:p
            "Add your "
            [:a
             {:href "\\https://dashboard.heroku.com/account\\"}
             "Heroku API Key"]
            " to set up deployment with Heroku."
            [:br]
            "You'll also need to set yourself as the Heroku deploy user from your project's settings page."]
           [:form
            {:on-submit (fn [event]
                          (.preventDefault event)
                          (submit-form! heroku-api-key-input))}
            (when heroku-api-key
              [:div
               [:input.disabled
                {:required  "",
                 :type      "text",
                 :value heroku-api-key}]
               [:label {:placeholder "Current Heroku key"}]])
            [:input
             {:required  true
              :type      "text",
              :value     heroku-api-key-input
              :disabled  processing-form?
              :class     (when processing-form?
                           "disabled")
              :onChange  (fn [event]
                           (om/set-state! owner :heroku-api-key-input (.. event -target -value)))}]
            [:label {:placeholder "Add new key"}]
            (forms/managed-button
             [:a {:data-loading-text "Saving...",
                  :data-failed-text  "Failed to save Heroku key",
                  :data-success-text "Saved",
                  :on-click          #(submit-form! heroku-api-key-input)}
              "Save Heroku key"])]]])))))

;; XXX 1. Not finished - how to tie form-submit to managed-button?
;; 2. Disable form while creating
;; 3. Clear form after success
(defn api-tokens [app owner]
  (reify
    om/IRender
    (render [_]
      (let [controls-ch   (om/get-shared owner [:comms :controls])
            tokens        (get-in app state/user-tokens-path)
            create-token! #(put! controls-ch [:api-token-creation-attempted {:label %}])]
        (html/html
         [:div#settings-api
          [:div.api-item
           [:h2 "API Tokens"]
           [:p
            "Create and revoke API tokens to access this account's details using our API. Apps using these tokens can act as you, and have full read- and write-permissions!"]
           [:form
            {:on-submit (fn [event]
                          (.preventDefault event)
                          (create-token! (om/get-state owner :token-label)))}
            [:input#api-token
             {:required  "",
              :name      "label",
              :type      "text",
              :value     (str (om/get-state owner :token-label))
              :on-change #(let [v (.. % -target -value)]
                            (om/set-state! owner :token-label v))}]
            [:label {:placeholder "Token name"}]
            (forms/managed-button
             [:a
              {:data-loading-text "Creating...",
               :data-failed-text  "Failed to add token",
               :data-success-text "Created",
               :on-click          #(create-token! (om/get-state owner :token-label))}
              "Create"])]]
          [:div.api-item
           {:data-bind "if: tokens().length"}
           (when (seq tokens)
             [:table.table
              [:thead [:th "Label"] [:th "Token"] [:th "Created"] [:th]]
              [:tbody
               {:data-bind "foreach: tokens"}
               (map (fn [token]
                      (let [token (om/value token)]
                        [:tr
                         [:td {:data-bind "text: label"} (:label token)]
                         [:td [:span.code {:data-bind "text: token"} (:token token)]]
                         [:td {:data-bind "text: time"} (datetime/full-datetime (js/Date.parse (:time token)))]
                         [:td
                          [:span
                           {:data-bind "click: $root.current_user().delete_token"}
                           (forms/managed-button
                            [:a.revoke-token
                             {:data-loading-text "Revoking...",
                              :data-failed-text  "Failed to revoke token",
                              :data-success-text "Revoked",
                              :on-click          #(put! controls-ch [:api-token-revocation-attempted {:token token}])}
                             "Revoke"])]]])) tokens)]])]])))))

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
      (let [subpage     (or (get-in app state/account-subpage-path)
                            :notifications)
            coms        {;; Finished converting
                         :notifications notifications
                         ;; Pending conversion
                         :heroku        heroku-key
                         :api           api-tokens
                         :plans         plans}
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
           [:div.settings-item-inner
            [:div#subpage
             (when subpage-com
               (om/build subpage-com app))]]]])))))
