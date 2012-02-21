(ns circle.api.test-server
  (:require [circle.api.server :as server])
  (:require [monger.collection :as monger])
  (:require [clj-http.client :as client])
  (:require [clj-json.core :as json]) ; deliberately use a different json lib to
                                      ; the server
  (:use [midje.sweet])
  (:use [circle.test-utils])
  (:require [circle.ruby :as r]))

(test-ns-setup)

(defn endpoint [name]
  (format "http://circlehost:8080/api/v1/%s" name))

(fact "helloworld should return hello"
  (let [resp (client/get (endpoint "hello") {:accept :json})]
    (-> resp :status) => 200
    (-> resp :headers (get "content-type")) => #"application/json"
    (-> resp :body (json/parse-string true)) => {:message "hello"}))

(fact "invalid mime type returns error"
  false => true)

(fact "invalid user returns error"
  false => true)

(defn user-login [user]
  (r/send (r/get-module :Warden) :test_mode!)
  (let [instance (circle.ruby/->instance user)
        helpers (-> :Warden (r/get-module) (r/get-module :Test) (r/get-module :Helpers))]
    (r/send helpers :login_as instance)))

(defn decode-cookie [cookie]
  cookie)

(fact "valid user gets response"
  (let [user (monger/find-one-as-map :users {:email "user@test.com"})
        decoded (decode-cookie "BAh7BiIQX2NzcmZfdG9rZW5JIjFTMjlIMStZRGJTZ1FBL3RaNHBwUVR0NWhZ\nUGpQSnJ1aFkzNnQ1RXdjcjV3PQY6BkVG\n")]
    (println decoded)
    (user-login user))
  false => true)
