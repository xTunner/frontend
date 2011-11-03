(ns circle.models.user
  (:use [circle.utils.validation :only (validate defn-v)])
  (:use [circle.utils.model-validation :only (validate!)])
  (:use [circle.utils.model-validation-helpers :only (require-keys key-types col-predicate)])
  (:require [somnium.congomongo :as mongo])
  (:require [noir.util.crypt :as crypt]))

(def user-validation [(require-keys [:email :password :display-name])
                      (col-predicate :roles #(or (nil? %) (coll? %)) ":roles must be a collection")
                      (col-predicate :projects #(or (nil? %) (coll? %)) ":projects must be a collection")])

(defmethod validate ::User [tag obj]
  (validate! user-validation obj))

(defn-v insert! [^::User u]
  (mongo/insert! :users (-> u
                            (update-in [:password] crypt/encrypt))))

(defn authenticate
  "Find a user, verify the password. Returns a User on success, or nil"
  [email password]
  (when-let [row (mongo/fetch-one :users :where {:email email})]
    (when (crypt/compare password (-> row :password))
      row)))