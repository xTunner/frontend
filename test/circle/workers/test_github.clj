(ns circle.workers.test-github
  (:use circle.backend.build.test-utils)
  (:require [circle.model.build :as build])
  (:use midje.sweet)
  (:use circle.workers.github))

(fact "start-build-from-hook works with dummy project"
  (ensure-test-user-and-project)
  (ensure-test-build)
  (let [json circle-dummy-project-json-str
        build (start-build-from-hook nil nil nil json test-build-id)]
    (-> @build :vcs_url) => truthy
    (-> build (build/successful?)) => true
    (-> @build :build_num) => integer?))

(fact "authorization-url works"
  ;; This test should be asserting that the URL contains the
  ;; test-github client id, but it currently contains the development
  ;; client id because lein midje doesn't set the environment properly
  ;; yet.
  (-> "http://localhost:3000/hooks/repos" authorization-url) =>
  "https://github.com/login/oauth/authorize?client_id=383faf01b98ac355f183&scope=repo&redirect_uri=http%3A%2F%2Flocalhost%3A3000%2Fhooks%2Frepos")