(ns circle.web.views.user
  (:use noir.core
        circle.web.views.common
        hiccup.page-helpers
        hiccup.form-helpers)
  (:use [circle.web.views.login :only (login)])
  (:require [circle.model.user :as user])
  (:use [ring.util.response :only (redirect)])
  (:require [noir.session :as session]))

(defn authorized?
  "Takes the username the user tried to access. Returns nil on success, or a redirect"
  [username]
  (let [desired-user (user/find-by-name username)
        current-user-id (session/get :user-id)]
    (if desired-user
      (do
        (println "desired-user=" desired-user)
        (println "current-user-id=" current-user-id)
        (when-not (= (:_id desired-user)
                     current-user-id)
          (redirect (url-for login))))
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