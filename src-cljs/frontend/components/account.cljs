(ns frontend.components.account
  (:require [frontend.async :refer [raise!]]
            [frontend.components.common :as common]
            [frontend.components.forms :as forms]
            [frontend.components.pieces.button :as button]
            [frontend.components.pieces.card :as card]
            [frontend.components.pieces.icon :as icon]
            [frontend.components.pieces.modal :as modal]
            [frontend.components.pieces.table :as table]
            [frontend.components.project.common :as project]
            [frontend.components.svg :refer [svg]]
            [frontend.datetime :as datetime]
            [frontend.models.organization :as org-model]
            [frontend.models.user :as user]
            [frontend.notifications :as notifications]
            [frontend.routes :as routes]
            [frontend.state :as state]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.html :refer [open-ext]]
            [frontend.utils.github :as gh-utils]
            [frontend.utils.seq :refer [select-in]]
            [om.core :as om :include-macros true])
  (:require-macros [frontend.utils :refer [defrender html component element]]))

(defn active-class-if-active [current-subpage subpage-link]
  (if (= current-subpage subpage-link)
    {:class "active"}))

(defn handle-email-notification-change [owner pref]
  (raise! owner [:preferences-updated {:basic_email_prefs pref}]))

(defn plans [app owner]
  (reify
    om/IRender
    (render [_]
      (let [user-and-orgs (sort-by (complement :org)
                                   (get-in app state/user-organizations-path))]
        (html
         [:div#settings-plans
          [:legend "Org Settings"]
          [:div.plans-item
           [:h3 "Set up a plan for one of your Organizations:"]
           [:p "You can set up plans for any organization that you admin."]
           [:div.plans-accounts
            (map
             (fn [org]
               (let [;; TODO: this link is sometimes dead. We should not link, or make
                     ;; the org settings page do something sane if there's not a plan.
                     org-url (routes/v1-org-settings-path {:org (:login org)
                                                           :vcs_type (:vcs_type org)})
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
        (html
         [:div#settings-heroku.settings
          [:div.heroku-item
           [:legend "Heroku API key"]
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
            (button/managed-button
             {:loading-text "Saving..."
              :failed-text "Failed to save key"
              :success-text "Saved"
              :on-click submit-form!}
             "Save Heroku Key")]]])))))

(defn api-tokens [app owner]
  (reify
    om/IRender
    (render [_]
      (let [tokens        (get-in app state/user-tokens-path)
            create-token! #(raise! owner [:api-token-creation-attempted {:label %}])
            new-user-token (get-in app state/new-user-token-path)]
        (html
         [:div#settings-api.settings
          [:legend "API Tokens"]
          [:div.api-item
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
            (button/managed-button
             {:loading-text "Creating..."
              :failed-text "Failed to add token"
              :success-text "Created"
              :on-click #(create-token! new-user-token)}
             "Create New Token")]

          [:div.api-item
           (when (seq tokens)
             (om/build table/table
                       {:rows tokens
                        :key-fn :token
                        :columns [{:header "Label"
                                   :cell-fn :label}

                                  {:header "Token"
                                   :type :shrink
                                   :cell-fn :token}

                                  {:header "Created"
                                   :type :shrink
                                   :cell-fn (comp datetime/medium-datetime js/Date.parse :time)}

                                  {:header "Remove"
                                   :type #{:shrink :right}
                                   :cell-fn
                                   (fn [token]
                                     (table/action-button
                                      "Remove"
                                      (icon/delete)
                                      #(raise! owner [:api-token-revocation-attempted {:token token}])))}]}))]]])))))


(def available-betas
  [{:id "project-cache-clear-buttons"
    :name "Clear Project Caches"
    :description "The Clear Project Caches beta allows you to totally
    clear the source and dependency caches for your projects.  These
    options are available on the project settings under the \"Clear
    Caches\" heading. This is different from the clear cache button
    when retrying a build which only clears the cache for retried
    build."}

   {:id "projects-page"
    :name "Projects Page"
    :description "The Projects Page helps give a quick overview of
    which projects you have building on CircleCI.  Access it by
    clicking on the \"Projects\" item in the left navigation bar."}

   {:id "merge-pull-request"
    :name "Merge Pull Request"
    :description "Now you can merge pull requests directly from a
    green build on CircleCI.  To use, look for the \"Merge PR\" button
    on a build page whose branch has a PR open." } ])

(defn beta-programs [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div
        [:h3.subheading "Current Beta Features"]
        (card/collection
         (for [{:keys [name description]} available-betas]
          (card/titled {:title name} description)))]))))

(defrender beta-terms-modal [{:keys [close-fn]} owner]
  (component
   (modal/modal-dialog {:title "Join Beta Program"
                        :body (element :body
                                       (html
                                        [:div
                                         [:h1 "Beta Terms"]
                                         [:p "Our beta program is a way to engage with our
                                              most thoughtful and dedicated users. We want
                                              to build the best features with your help.
                                              To that end, in joining the beta program you should be
                                              comfortable with these expectations:"]
                                         [:ul
                                          [:li "You’ll find out about new features through e-mail and in-app messages"]
                                          [:li "Please give us feedback about new features when we release them"]
                                          [:li "Keep the private beta, private. Please no tweets, blogs, or other public
                                                    posting, but we do encourage you to talk with your coworkers!"]]]))
                        :actions [(button/button {:on-click close-fn} "Cancel")
                                  (button/button
                                   {:kind :primary
                                    :on-click #(do
                                                 (raise! owner [:preferences-updated {state/user-in-beta-key true}])
                                                 ((om/get-shared owner :track-event) {:event-type :beta-accept-terms-clicked})
                                                 (close-fn))}
                                   "Accept")]
                        :close-fn close-fn})))

(defn join-beta-program [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [show-modal?]}]
      (card/titled {:title "Beta Program"}
                   (html
                    [:div
                     (when show-modal?
                       (om/build beta-terms-modal {:close-fn #(om/set-state! owner :show-modal? false)}))
                     [:p "We invite you to join Inner Circle, our new
                          beta program. As a member of CircleCI’s
                          Inner Circle you get exclusive access to new
                          features and settings before they are
                          released publicly!"]
                     (button/button
                      {:on-click #(do
                                    (om/set-state! owner :show-modal? true)
                                    ((om/get-shared owner :track-event) {:event-type :beta-join-clicked}))
                       :kind :primary}
                      "Join Beta Program")])))))

(defn beta-program-member [app owner]
  (reify
    om/IRender
    (render [_]
      (html
       [:div
        (card/titled {:title "Beta Program"}
                     (html
                      [:div
                       [:p "Thanks for being part of the beta program.
                            We'll let you know when we release updates
                            so you'll be the first to see new
                            features!" ]
                       [:p "We'd love to know what you think - " [:a {:href "mailto:beta@circleci.com"} "send us your feedback"] "!"]
                       (button/button
                        {:on-click #(do
                                      (raise! owner [:preferences-updated {state/user-in-beta-key false}])
                                      ((om/get-shared owner :track-event) {:event-type :beta-leave-clicked}))
                         :kind :primary}
                        "Leave Beta Program")]))
        [:hr]
        (om/build beta-programs app)]))))

(defn beta-program [app owner]
  (reify
    om/IRender
    (render [_]
      (let []
        (html
         [:div
          (let [enrolled? (get-in app state/user-in-beta-path)]
            (if enrolled?
              (om/build beta-program-member app)
              (om/build join-beta-program app)))])))))

(defn preferred-email-address [owner user]
  [:div.card
   [:div.header
    [:h2
     "Default Email Address"]]
   [:div.body
    [:div.section
     [:form#email_address.form-horizontal
      [:div
       [:p "These are the email addresses associated with your GitHub and Bitbucket organizations."]
       [:select.form-control
        {:on-change #(let [email (-> % .-target .-value)]
                       (raise! owner [:preferences-updated {:selected_email email}]))
         :value (user/primary-email user)}
        (for [email (:all_emails user)]
          [:option
           {:value email}
           email])]]]]]])

(defn default-email-pref [owner email-pref]
  [:div.card
   [:div.header
    [:h2
     "Default Email Notifications"]]
   [:div.body
    [:div.section
     [:form
      [:div.radio
       [:label
        [:input
         {:name "email_pref"
          :type "radio"
          :checked (= email-pref "all")
          :on-change (partial handle-email-notification-change owner "all")}]
        "Send me a personalized email for every build in my projects."]]
      [:div.radio
       [:label
        [:input
         {:name "email_pref"
          :type "radio"
          :checked (= email-pref "smart")
          :on-change (partial handle-email-notification-change owner "smart")}]
        "Send me a personalized email every time a build on a branch I've pushed to fails; also once they're fixed."]]
      [:div.radio
       [:label
        [:input
         {:name "email_pref"
          :type "radio"
          :checked (= email-pref "none")
          :on-change (partial handle-email-notification-change owner "none")}]
        "Don't send me emails."]]]]]])

(defn web-notifications [owner web-notifications-enabled? granted]
  (let [disabled? (not= granted "granted")]
    [:div.card
     [:div.header
      [:h2
       "Web Notifications"]]
     (if (notifications/notifiable-browser?)
       [:div.body
        (case granted
          "denied" [:div.section
                    "It looks like you've denied CircleCI access to send you web notifications.
                    Before you can change your web notification preferences please "
                    [:a {:href "https://circleci.com/docs/web-notifications/#turning-notifications-permissions-back-on"
                         :target "_blank"}
                     "turn on permissions for your browser."]]
          "default" [:div.section
                     "You haven't given CircleCI access to notify you through the browser — "
                     [:a {:href "javascript:void(0)"
                          :on-click #(notifications/request-permission
                                       (fn [response]
                                         (when (= response "granted") (raise! owner [:set-web-notifications-permissions {:enabled? true :response response}]))))}
                      "click here to turn permissions on."]]
          nil)
        [:div.section
         [:form
          [:div.radio
           [:label
            [:input.radio-circle
             {:name "web_notif_pref"
              :type "radio"
              :checked web-notifications-enabled?
              :on-change #(raise! owner [:set-web-notifications-permissions {:enabled? true}])
              :disabled disabled?}]
            (when disabled? [:span [:i.material-icons.lock "lock" ] " "])
            [:span.label-contents " Show me notifications when a build finishes"]]]
          [:div.radio
           [:label
            [:input.radio-circle
             {:name "web_notif_pref"
              :type "radio"
              :checked (not web-notifications-enabled?)
              :on-change #(raise! owner [:set-web-notifications-permissions {:enabled? false}])
              :disabled disabled?}]
            (when disabled? [:span [:i.material-icons.lock "lock" ] " "])
            [:span.label-contents " Don't show me notifications when a build finishes"]]]]]]
       ;; -- If browser doesn't support the Web Notifications API:
       [:div.body
        [:div.section "You browser doesn't support web notifications."]])]))

(defn granular-email-prefs [{:keys [projects user] :as x} owner]
  (let [followed-orgs (into (sorted-set-by (fn [[x-vcs-type x-name]
                                                [y-vcs-type y-name]]
                                             (let [vcs-compare (compare x-vcs-type y-vcs-type)]
                                               (if (= vcs-compare 0)
                                                 (compare x-name y-name)
                                                 vcs-compare))))
                            (map (fn [{:keys [vcs_type username]}]
                                   [(keyword vcs_type)
                                    (keyword username)])
                                 projects))]
    (reify
      om/IRenderState
      (render-state [_ {:keys [selected-org] :or {selected-org (first followed-orgs)}}]
        (html
         [:div.card
          [:div.header
           [:h2 "Email preferences"]
           [:div
            [:label "Choose an organization"]
            [:select.form-control
             {:on-change #(let [value (-> % .-target .-value)]
                            (om/set-state! owner
                                           [:selected-org]
                                           (org-model/uglify-org-id value)))}
             (for [org-id followed-orgs
                   :let [org-id-pretty (org-model/prettify-org-id org-id)]]
               [:option
                {:value org-id-pretty}
                org-id-pretty])]]]
          [:div.body
           [:div.section
            [:h3 "Notified email"]
            [:select.form-control
             {:on-change #(let [val (-> % .-target .-value)
                                args {:email (if (= "Default" val)
                                               nil
                                               val)}]
                            (raise! owner [:org-preferences-updated {:org selected-org
                                                                     :prefs args}]))
              :value (if-let [selected-email (get-in user (-> [:organization_prefs]
                                                              (into selected-org)
                                                              (conj :email)))]
                       selected-email
                       "Default")}
             (for [email (cons "Default" (:all_emails user))]
               [:option
                {:value email}
                email])]]
           [:div.section
            [:h3 "Project notification emails"]
            [:div.table-header
             [:h4 "Project"]
             [:h4 "Email preference"]]
            (for [project projects
                  :when (= [(keyword (:vcs_type project)) (keyword (:username project))] selected-org)]
              (om/build project/email-pref {:project project :user user} {:react-key (:reponame project)}))]]])))))

(defn notifications [app owner]
  (reify
    om/IRender
    (render [_]
      (let [user (get-in app state/user-path)
            projects (get-in app state/projects-path)
            notifications-enabled? (get-in app state/web-notifications-enabled-path)]
        (html
         [:div#settings-notification
          [:legend "Notification Settings"]
          (preferred-email-address owner user)
          (default-email-pref owner (:basic_email_prefs user))
          (om/build granular-email-prefs {:projects projects :user user})
          (web-notifications owner notifications-enabled? (notifications/notifications-permission))])))))

(defn account [app owner]
  (reify
    om/IRender
    (render [_]
      (let [subpage     (get-in app state/navigation-subpage-path)
            coms        {:notifications notifications
                         :heroku        heroku-key
                         :api           api-tokens
                         :plans         plans
                         :beta          beta-program}
            subpage-com (get coms subpage)]
        (html
         [:div#account-settings
          [:div.row (om/build common/flashes (get-in app state/error-message-path))]
          [:div#subpage
           (om/build subpage-com (select-in app [state/general-message-path state/user-path state/projects-path state/web-notifications-enabled-path]))]])))))
