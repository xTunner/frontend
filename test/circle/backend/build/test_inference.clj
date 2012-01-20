(ns circle.backend.build.test-inference
  (:use midje.sweet)
  (:use circle.backend.build.inference))

(fact "inferred actions have source :inferred"
  (let [example-repo "test/circle/backend/build/inference/test_dirs/database_yml_1/"
        actions (infer-actions example-repo)]
    (every? :source actions) => true
    (->> actions
         (map :source)
         (into #{})
         (#(contains? % :inferred)))  => true))