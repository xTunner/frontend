(ns circle.model.user
  (:use [circle.util.validation :only (validate defn-v)])
  (:use [circle.util.model-validation :only (validate!)])
  (:use [circle.util.model-validation-helpers :only (require-keys key-types col-predicate)])
  (:require [somnium.congomongo :as mongo])
  (:require [noir.util.crypt :as crypt]))

(defn valid-username? [username]
  (boolean (and (string? username) (re-find #"^\p{Alnum}+$" username))))

(defmethod validate ::username [tag obj]
  (when-not (and (string? obj) (valid-username? obj))
    (format "%s is not a valid username" obj)))

(def user-validation [(require-keys [:username :email :password :display-name])
                      (col-predicate :username valid-username? "username must only contain alphanumeric characters")
                      (col-predicate :roles #(or (nil? %) (coll? %)) ":roles must be a collection")
                      (col-predicate :projects #(or (nil? %) (coll? %)) ":projects must be a collection")])

(defmethod validate ::User [tag obj]
  (validate! user-validation obj))

(defn-v insert! [^::User u]
  (mongo/insert! :users (-> u
                            (update-in [:password] crypt/encrypt))))

(defn find-by-name [username]
  (mongo/fetch-one :users :where {:username username}))

(defn authorized?
  "Is the user with user-id allowed to view the page owne"
  [user-id username])

(defn authenticate
  "Find a user, verify the password. Returns a User on success, or nil"
  [username password]
  (when-let [row (mongo/fetch-one :users :where {:username username})]
    (when (crypt/compare password (-> row :password))
      row)))