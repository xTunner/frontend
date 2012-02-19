(ns circle.backend.build.test-inference
  (:use midje.sweet)
  (:use circle.backend.build.inference)
  (:require [circle.test-utils :as test]))

(fact "inferred actions have source :inferred"
  (let [example-repo (test/test-repo "database_yml_1")
        actions (infer-actions example-repo)]
    (every? :source actions) => true
    (->> actions
         (map :source)
         (into #{})
         (#(contains? % :inferred)))  => true))

;; our own tests are broken if this doesn't pass
(fact "inference runs lein deps before db:create"
  (let [actions (infer-actions ".")]
    (map :name actions) => (contains [(contains "lein deps") (contains "rake db:create")] :gaps-ok)))
