(ns circle.web.views.login
  (:use noir.core
        circle.web.views.common
        hiccup.page-helpers
        hiccup.form-helpers)
  (:use [circle.models.user :only (authenticate)])
  (:use [ring.util.response :only (redirect)])
  (:require [noir.session :as session]))

(declare login-form)

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
  (if-let [user (authenticate username password)]
    (do
      (session/put! :user-id (-> user :_id))
      (redirect (url-for circle.web.views.user/user-page :username username)))
    (do
      (session/flash-put! false)
      (redirect "/login"))))