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
       [(text-field {:id "email"
                     :type "text"
                     :onfocus "this.style.background = 'white'; if (this.value == 'Email address') { this.value=''};"
                     :onblur "if (this.value == '') { this.value = 'Email address'};"}
                    "email" "Email address")
        (text-field {:id "password"
                     :type "Password"
                     :onfocus "this.style.background = 'white'; if (this.value == 'Email address') { this.value=''};"
                     :onblur "if (this.value == '') { this.value = 'Password'};"}
                    "password" "")])]
     [:fieldset
      [:input.call_to_action {:type "submit"
                              :value "Login"}]]]])

(defpage [:post "/login"] {:keys [email password]}
  (if-let [user (authenticate email password)]
    (do
      (session/put! :user-id (-> user :_id))
      (redirect "/user/email"))
    (do
      (session/flash-put! false)
      (redirect "/login"))))