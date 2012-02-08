(ns circle.backend.build.inference.test-php
  (:use midje.sweet)
  (:use circle.backend.build.inference.php)
  (:require [circle.backend.build.inference :as inference])
  (:require [circle.backend.build.test-utils :as test])
  (:require [circle.backend.git :as git])
  (:require fs))

(fact "The default test command is `phpunit test/` from the repo directory"
      (let [repo (test/test-repo "simple_php")
            inferred-test-actions (filter #(= (:type %) :test)
                                          (inference/infer-actions* :php repo))
            action-names (->> inferred-test-actions (map :name) (into #{}))]
        action-names => (contains "PHPUnit")))

