(ns frontend.components.account
  (:require [cljs.core.async :as async :refer [>! <! alts! chan sliding-buffer close!]]
            [clojure.set :as set]
            [clojure.string :as string]
            [frontend.async :refer [raise!]]
            [frontend.analytics :as analytics]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.project.common :as project]
            [frontend.components.svg :refer [svg]]
            [frontend.datetime :as datetime]
            [frontend.routes :as routes]
            [frontend.models.feature :as feature]
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
          [:div.plans-item [:h1 "Org Settings"]]
          [:div.plans-item
           [:h3 "Set up a plan for one of your Organizations:"]
           [:p "You can set up plans for any organization that you admin."]
           [:div.plans-accounts
            (map
             (fn [org]
               (let [;; TODO: this link is sometimes dead. We should not link, or make
                     ;; the org settings page do something sane if there's not a plan.
                     org-url (routes/v1-org-settings {:org (:login org)})
                     avatar-url (gh-utils/make-avatar-url org :size 25)]
                 [:div
                  [:a
                   {:href org-url}
                   [:img
                    {:src    avatar-url
                     :height 25
                     :width  25}]]
                  " "
                  [:a.account-plan-pricing-org-link
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
         [:div#settings-heroku.settings
          [:div.heroku-item
           [:h1 "Heroku API key"]
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
            [:input#heroku-key
             {:required  true
              :type      "text",
              :value     heroku-api-key-input
              :on-change  #(utils/edit-input owner (conj state/user-path :heroku-api-key-input) %)}]
            [:label {:placeholder "Add new key"}]
            (forms/managed-button
             [:input.btn
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
         [:div#settings-api.settings
          [:div.api-item
           [:h1 "API Tokens"]
           [:p
            "Create and revoke API tokens to access this account's details using our API."
            [:br]
            "Apps using these tokens can act as you, and have full read- and write-permissions!"]

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
               :value "Create new token"}])]

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
                       [:i.material-icons "remove_circle"]
                       "Revoke"]]]]))]])]]])))))


(def available-betas
  [;; {:id "insights"
   ;;  :name "Insights"
   ;;  :description "Also this text. Insights is super fun for the whole family! "}
   ])

(defn set-beta-preference! [owner betas id value]
  (raise! owner [:preferences-updated {state/user-betas-key
                                       (if value
                                         (conj (set betas) id)
                                         (disj (set betas) id))}]))

(defn beta-programs [app owner]
  (reify
    om/IRender
    (render [_]
      (let [betas (get-in app state/user-betas-path)]
        (html
         [:div
          [:h4.beta-programs "Available Beta Programs"]
          (interpose
           [:hr]
           (map
            (fn [program]
              (let [participating? (contains? (set betas) (:id program))]
                [:div
                 [:h1 (:name program)
                  (when participating?
                    (om/build svg {:class "badge-enrolled"
                                   :src (common/icon-path "Status-Passed")}))]
                 [:p (:description program)]
                 [:input.btn.btn-info
                  {:on-click #(do
                                (set-beta-preference! owner betas (:id program) (not participating?)))
                   :type "submit"
                   :value (if participating?
                            (str "Leave " (:name program) " Beta")
                            (str "Join " (:name program) " Beta"))}]]))
            available-betas))])))))

(defn set-beta-program-preference! [owner pref]
  (raise! owner [:preferences-updated {state/user-in-beta-key pref}]))

(defn join-beta-program [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [clicked-join?]}]
      (html/html
       [:div
        [:p
         "We invite you to join Inner Circle, our new beta program. As a
         member of CircleCI’s Inner Circle you get exclusive access to new
         features and settings before they are released publicly!"]
        [:form
         (if (not clicked-join?)
           [:input.btn
            {:on-click #(do
                          (om/set-state! owner :clicked-join? true)
                          (analytics/track {:event-type :beta-join-clicked
                                            :owner owner})
                          false)
             :type "submit"
             :value "Join Beta Program"}]
           [:div
            [:div.card
             [:h1 "Beta Terms"]
             [:p
              "Our beta program is a way to engage with our most
               thoughtful and dedicated users. We want to build the
               best features with your help. To that end, in joining
               the beta program you should be comfortable with these
               expectations:"]
             [:ul
              [:li "You’ll find out about new features through e-mail and in-app messages"]
              [:li "Please give us feedback about new features when we release them"]
              [:li "Keep the private beta, private. Please no tweets, blogs, or other public
                    posting, but we do encourage you to talk with your
                    coworkers!"]]]
            [:p]
            [:input.btn
            {:on-click #(do
                          (set-beta-program-preference! owner true)
                          (analytics/track {:event-type :beta-accept-terms-clicked
                                            :owner owner})
                          false)
             :type "submit"
             :value "Accept"}]])]]))))

(defn beta-program-member [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div
        [:div
         [:p "Thanks for being part of the beta program.  We'll let you know when we release updates so you'll be the first to see new features!" ]
         [:p "We'd love to know what you think - " [:a {:href "mailto:beta@circleci.com"} "send us your feedback"] "!"]
         [:form
          [:input.btn.btn-danger
           {:on-click #(do
                         (set-beta-program-preference! owner false)
                         (analytics/track {:event-type :beta-leave-clicked
                                           :owner owner})
                         false)
            :type "submit"
            :value "Leave Beta Program"}]]]
        (comment
          ;; uncomment to turn on beta sub programs
          [:hr]
          (om/build beta-programs app))]))))

(defn beta-program [app owner]
  (reify
    om/IRender
    (render [_]
      (let []
        (html/html
         [:div#settings-beta-program
          [:div
           (let [message (get-in app state/general-message-path)
                 enrolled? (get-in app state/user-in-beta-path)]
             (list
              [:h1 "Beta Program"
               (when (and enrolled? (not message))
                 (om/build svg {:class "badge-enrolled"
                                :src (common/icon-path "Status-Passed")}))]
              (when message
                [:div.alert.alert-success
                 [:span
                  (om/build svg {:src (if enrolled?
                                        (common/icon-path "Status-Passed")
                                        (common/icon-path "Info-Info"))})
                  [:span message]]])))
           (if (get-in app state/user-in-beta-path)
             (om/build beta-program-member app)
             (om/build join-beta-program app))]])))))

(defn preferred-email-address [owner user]
  [:div.notification-item
   [:form#email_address.form-horizontal
    [:h1#email-address-tooltip-parent
     "Email Addresses"
     [:i.material-icons#email-addresses-tooltip-hack
      {:title "These are the email addresses associated with your GitHub account."} "info_outline"]]
    [:div
     (for [email (:all_emails user)]
       [:div.radio
        [:label
         [:input
          {:checked (= (:selected_email user) email)
           :value email
           :name "selected_email"
           :type "radio"
           :on-click #(raise! owner [:preferences-updated {:selected_email email}])}]
         email]])]]])

(defn default-email-pref [owner email-pref]
  [:div.notification-item
   [:form
    [:h1 "Default Email Notifications"]
    [:div.radio
     [:label
      [:input
       {:name "email_pref",
        :type "radio"
        :checked (= email-pref "all")
        :on-change (partial handle-email-notification-change owner "all")}]
      "Send me a personalized email for every build in my projects."]]
    [:div.radio
     [:label
      [:input
       {:name "email_pref",
        :type "radio"
        :checked (= email-pref "smart")
        :on-change (partial handle-email-notification-change owner "smart")}]
      "Send me a personalized email every time a build on a branch I've pushed to fails; also once they're fixed."]]
    [:div.radio
     [:label
      [:input
       {:name "email_pref",
        :type "radio"
        :checked (= email-pref "none")
        :on-change (partial handle-email-notification-change owner "none")}]
      "Don't send me emails."]]]])

(defn project-email-prefs [{:keys [projects user]}]
  [:div.notification-item
   [:form
    [:h1 "Project Email Preferences"]
    [:p "You can override your default email preferences for individual projects here."]
    [:p "Other project settings can be configured via the project's 'Settings' page."]
    [:div.row
     [:div.col-md-6
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
                         :plans         plans
                         :beta          beta-program}
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
             [:a {:href (routes/v1-account-subpage {:subpage "plans"})} "Plan Pricing"]]
            (when (feature/enabled? :beta-program-available)
              [:li#beta (active-class-if-active subpage :beta)
               [:a {:href (routes/v1-account-subpage {:subpage "beta"})} "Beta Program"]])]]

          [:div.row (om/build common/flashes (get-in app state/error-message-path))]
          [:div.settings-item
           [:div.settings-item-inner
            [:div#subpage
             (om/build subpage-com (select-in app [state/general-message-path state/user-path state/projects-path]))]]]])))))
