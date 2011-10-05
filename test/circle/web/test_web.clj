(ns circle.web.test-web
  (:require circle.init)
  (:require [clj-http.client :as http])
  (:use midje.sweet))

(circle.init/init)

(def site "http://localhost:8080")

(fact "/ returns 200"
  (http/get site) => (contains {:status 200}))