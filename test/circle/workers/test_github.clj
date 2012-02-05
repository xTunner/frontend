(ns circle.workers.test-github
  (:use circle.backend.build.test-utils)
  (:require [circle.model.build :as build])
  (:use [circle.util.mongo])
  (:require [somnium.congomongo :as mongo])
  (:use midje.sweet)
  (:use circle.workers.github))

(test-ns-setup)

(fact "start-build-from-hook works with dummy project"
  (let [json circle-dummy-project-json-str
        id (object-id)
        _   (mongo/insert! :builds {:_id id})
        build (start-build-from-hook nil nil nil json (str id))]
    (-> @build :vcs_url) => truthy
    (-> build (build/successful?)) => true
    (-> @build :build_num) => integer?
    (-> @build :_id) => id))

(fact "authorization-url works"
  (-> "http://localhost:3000/hooks/repos" authorization-url) =>
  "https://github.com/login/oauth/authorize?client_id=586bf699b48f69a09d8c&scope=repo&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fhooks%2Frepos")