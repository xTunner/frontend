(ns circleci.web.test-web
  (:require [clj-http.client :as http])
  (:use midje.sweet))

(def site "http://localhost:8080")

(fact "/ returns 200"
  (http/get site) => (contains {:status 200}))