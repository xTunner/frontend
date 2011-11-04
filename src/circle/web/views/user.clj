(ns circle.web.views.user
  (:use noir.core
        circle.web.views.common
        hiccup.page-helpers
        hiccup.form-helpers)
  (:require [circle.model.user :as user])
  (:use [ring.util.response :only (redirect)])
  (:require [noir.session :as session]))

(declare login-form user-page)

(defpage "/login" []
  (layout {}
          [:div#pitch_wrap
           [:div#pitch
            (login-form)]]))

(defpartial login-form []
  [:div.blue-box
    [:h2.takepart "Login"]
    [:form {:action "/login"
            :method "POST"
            :onsubmit "if (this.email.value == \"Email address\") { this.email.style.background = 'red'; return false; }"}
     [:fieldset#actualform
      (unordered-list
       [(text-field {:id "username"
                     :type "text"
                     :onfocus "this.style.background = 'white'; if (this.value == 'Username') { this.value=''};"
                     :onblur "if (this.value == '') { this.value = 'Username'};"}
                    "username" "Username")
        (text-field {:id "password"
                     :type "Password"
                     :onfocus "this.style.background = 'white'; if (this.value == 'Password') { this.value=''};"
                     :onblur "if (this.value == '') { this.value = 'Password'};"}
                    "password" "")])]
     [:fieldset
      [:input.call_to_action {:type "submit"
                              :value "Login"}]]]])

(defpage login [:post "/login"] {:keys [username password]}
  (if-let [user (user/authenticate username password)]
    (do
      (session/put! :user-id (-> user :_id))
      (redirect (url-for user-page :username username)))
    (redirect "/login")))

(defpage logout [:post "/logout"] []
  (circle.web.user-session/logout)
  (redirect "/"))

(defn authorized?
  "Takes the username the user tried to access. Returns nil on success, or a redirect"
  [username]
  (let [desired-user (user/find-by-name username)
        current-user-id (session/get :user-id)]
    (if desired-user
      (when-not (= (:_id desired-user)
                   current-user-id)
        (redirect (url-for login)))
      (redirect "/404"))))

(pre-route "/user/:username" request
           (let [username (-> request :params :username)]
             (authorized? username)))

(pre-route "/user/:username/*" request
           (let [username (-> request :params :username)]
             (authorized? username)))

(defpage user-page "/user/:username" {:keys [username]}
  (layout {}
          [:div#pitch_wrap
           [:div#pitch "Not much here yet."]]
          [:div#content_wrap
           [:div#content "Not much here yet." "username=" username]]))