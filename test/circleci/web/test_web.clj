(ns circleci.web.test-web
  (:require circleci.init)
  (:require [clj-http.client :as http])
  (:use midje.sweet))

(circleci.init/init)

(def site "http://localhost:8080")

(fact "/ returns 200"
  (http/get site) => (contains {:status 200}))