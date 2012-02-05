(ns circle.backend.build.test-inference
  (:use midje.sweet)
  (:use circle.backend.build.inference)
  (:require [circle.backend.build.test-utils :as test]))

(fact "repositories containing php files are assumed to be php apps"
      (let [php-repo (test/test-repo "simple_php")]
        (infer-repo-type php-repo) => :php))

(fact "repositories that aren't php are assumed to be rails"
      (let [some-repo (test/test-repo "empty")]
        (infer-repo-type some-repo) => :rails))

(fact "inferred actions have source :inferred"
  (let [example-repo (test/test-repo "database_yml_1")
        actions (infer-actions example-repo)]
    (every? :source actions) => true
    (->> actions
         (map :source)
         (into #{})
         (#(contains? % :inferred)))  => true))
