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
