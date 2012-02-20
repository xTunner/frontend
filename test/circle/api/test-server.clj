(ns circle.api.test-server
  (:require [circle.api.server :as server])
  (:require [clj-http.client :as client])
  (:require [clj-json.core :as json]) ; deliberately use a different json lib to
                                      ; the server
  (:use [midje.sweet]))

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

(fact "valid user gets response"
  false => true)
