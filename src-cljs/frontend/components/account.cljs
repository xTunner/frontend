(ns frontend.components.account
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.string :as string]
            [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.project.common :as project]
            [frontend.datetime :as datetime]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.seq :refer [select-in]]
            [frontend.utils.vcs-url :as vcs-url]
            [om.core :as om :include-macros true]
            [om.dom :as dom]
            [sablono.core :as html :refer-macros [html]]))

(defn active-class-if-active [current-subpage subpage-link]
  (if (= current-subpage subpage-link)
    {:class "active"}))

(defn handle-email-notification-change [owner pref]
  (raise! owner [:preferences-updated {:basic_email_prefs pref}]))

(defn plans [app owner]
  (reify
    om/IRender
    (render [_]
      (let [user-and-orgs (conj (get-in app state/user-organizations-path)
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
                                                                 :subpage "plan"})
                     avatar-url (gh-utils/make-avatar-url org :size 25)]
                 [:div
                  [:a
                   {:href org-url}
                   [:img
                    {:src    avatar-url
                     :height 25
                     :width  25}]]
                  " "
                  [:a
                   {:href org-url}
                   [:span (:login org)]]
                  [:br]
                  [:br]]))
             user-and-orgs)]]])))))

(defn heroku-key [app owner opts]
  (reify
    om/IRenderState
    (render-state [_ _]
      (let [heroku-api-key (get-in app (conj state/user-path :heroku_api_key))
            heroku-api-key-input (get-in app (conj state/user-path :heroku-api-key-input))
            submit-form! #(raise! owner [:heroku-key-add-attempted {:heroku_api_key heroku-api-key-input}])
            project-page? (:project-page? opts)]
        (html/html
         [:div#settings-heroku
          [:div.heroku-item
           [:h2 "Heroku API key"]
           [:p
            "Add your " [:a {:href "https://dashboard.heroku.com/account"} "Heroku API Key"]
            " to set up deployment with Heroku."
            [:br]
            ;; Don't tell them to go to the project page if they're already there
            (when-not project-page?
              "You'll also need to set yourself as the Heroku deploy user from your project's settings page.")]
           [:form
            (when heroku-api-key
              [:div
               [:input.disabled
                {:required  true
                 :type      "text",
                 :value heroku-api-key}]
               [:label {:placeholder "Current Heroku key"}]])
            [:input
             {:required  true
              :type      "text",
              :value     heroku-api-key-input
              :on-change  #(utils/edit-input owner (conj state/user-path :heroku-api-key-input) %)}]
            [:label {:placeholder "Add new key"}]
            (forms/managed-button
             [:input
              {:data-loading-text "Saving...",
               :data-failed-text  "Failed to save Heroku key",
               :data-success-text "Saved",
               :type "submit"
               :value "Save Heroku key"
               :on-click #(do (submit-form!) false)}])]]])))))

(defn api-tokens [app owner]
  (reify
    om/IRender
    (render [_]
      (let [tokens        (get-in app state/user-tokens-path)
            create-token! #(raise! owner [:api-token-creation-attempted {:label %}])
            new-user-token (get-in app state/new-user-token-path)]
        (html/html
         [:div#settings-api
          [:div.api-item
           [:h2 "API Tokens"]
           [:p
            "Create and revoke API tokens to access this account's details using our API. Apps using these tokens can act as you, and have full read- and write-permissions!"]
           [:form
            [:input#api-token
             {:required  true
              :name      "label",
              :type      "text",
              :value     (str new-user-token)
              :on-change #(utils/edit-input owner state/new-user-token-path %)}]
            [:label {:placeholder "Token name"}]
            (forms/managed-button
             [:input.btn
              {:data-loading-text "Creating...",
               :data-failed-text  "Failed to add token",
               :data-success-text "Created",
               :on-click          #(do (create-token! new-user-token)
                                       false)
               :type "submit"
               :value "Create"}])]]
          [:div.api-item
           (when (seq tokens)
             [:table.table
              [:thead [:th "Label"] [:th "Token"] [:th "Created"] [:th]]
              [:tbody
               (for [token tokens]
                 (let [token (om/value token)]
                   [:tr
                    [:td {:data-bind "text: label"} (:label token)]
                    [:td [:span.code {:data-bind "text: token"} (:token token)]]
                    [:td {:data-bind "text: time"} (datetime/medium-datetime (js/Date.parse (:time token)))]
                    [:td
                     [:span
                      [:a.revoke-token
                       {:data-loading-text "Revoking...",
                        :data-failed-text  "Failed to revoke token",
                        :data-success-text "Revoked",
                        :on-click          #(raise! owner [:api-token-revocation-attempted {:token token}])}
                       [:i.fa.fa-times-circle]
                       " Revoke"]]]]))]])]])))))

(defn preferred-email-address [owner user]
  [:div.notification-item
   [:form#email_address.form-horizontal
    [:fieldset
     [:legend
      "Email Addresses"
      [:i.fa.fa-info-circle#email-addresses-tooltip-hack
       {:title "Addresses added to your GitHub account will be reflected here"}]]
     [:div
      (for [email (:all_emails user)]
        [:label.radio
         [:input
          {:checked (= (:selected_email user) email)
           :value email
           :name "selected_email"
           :type "radio"
           :on-click #(raise! owner [:preferences-updated {:selected_email email}])}]
         [:span email]])]]]])

(defn default-email-pref [owner email-pref]
  [:div.notification-item
   [:form
    [:fieldset
     [:legend "Default Email Notifications"]
     [:label.radio
      [:input
       {:name "email_pref",
        :type "radio"
        :checked (= email-pref "all")
        :on-change (partial handle-email-notification-change owner "all")}]
      "Send me a personalized email for every build in my projects."]
     [:label.radio
      [:input
       {:name "email_pref",
        :type "radio"
        :checked (= email-pref "smart")
        :on-change (partial handle-email-notification-change owner "smart")}]
      "Send me a personalized email every time a build on a branch I've pushed to fails; also once they're fixed."]
     [:label.radio
      [:input
       {:name "email_pref",
        :type "radio"
        :checked (= email-pref "none")
        :on-change (partial handle-email-notification-change owner "none")}]
      "Don't send me emails."]]]])

(defn project-email-prefs [{:keys [projects user]}]
  [:div.notification-item
   [:form
    [:fieldset
     [:legend "Project Email Preferences"]
     [:p "You can override your default email preferences for individual projects here."]
     [:p "Other project settings can be configured via the project's 'Settings' page."]
     [:div
      (for [project projects]
        (om/build project/email-pref {:project project :user user}))]]]])

(defn notifications [app owner]
  (reify
    om/IDidMount
    (did-mount [_]
      (utils/tooltip "#email-addresses-tooltip-hack" {:placement "right" :trigger "hover"}))
    om/IRender
    (render [_]
      (let [user (get-in app state/user-path)
            projects (get-in app state/projects-path)]
        (html/html
         [:div#settings-notification
          (preferred-email-address owner user)
          (default-email-pref owner (:basic_email_prefs user))
          (project-email-prefs {:projects projects :user user})])))))

(defn account [app owner]
  (reify
    om/IRender
    (render [_]
      (let [subpage     (or (get-in app state/account-subpage-path)
                            :notifications)
            coms        {:notifications notifications
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
          [:div.row (om/build common/flashes (get-in app state/error-message-path))]
          [:div.settings-item
           [:div.settings-item-inner
            [:div#subpage
             (om/build subpage-com (select-in app [state/user-path state/projects-path]))]]]])))))
