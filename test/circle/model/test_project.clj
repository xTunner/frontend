(ns circle.model.test-project
  (:use circle.model.project)
  (:require [circle.backend.build.test-utils :as test])
  (:use midje.sweet))

(test/test-ns-setup)

(fact "ssh-key-for-url finds the key for dummy project"
  (ssh-key-for-url "https://github.com/arohner/circle-dummy-project") => truthy)

(fact "get-by-url works"
  (let [url "https://github.com/arohner/circle-dummy-project"]
    (get-by-url! url) => (contains {:vcs_url url})))

(fact "next-build-num works"
  (next-build-num (get-by-url! "https://github.com/arohner/circle-dummy-project")) => integer?)