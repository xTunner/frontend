(ns circle.web.user-session
  (:require [noir.session :as session])
  (:require [circle.model.user]))

(defn logged-in? []
  (boolean (session/get :user-id)))

(defn admin? []
  (session/get :admin?))

(defn logout []
  (session/clear!))

(defn login [user-id]
  (session/put! :user-id user-id))