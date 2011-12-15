(ns circle.model.test_project
  (:use circle.model.project)
  (:require [circle.backend.build.test-utils :as test])
  (:use midje.sweet))

(test/ensure-test-project)

(fact "ssh-key-for-url finds the key for dummy project"
  (ssh-key-for-url "https://github.com/arohner/circle-dummy-project") => truthy)