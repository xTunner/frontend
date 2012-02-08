(ns circle.backend.build.inference.test-php
  (:use midje.sweet)
  (:use circle.backend.build.inference.php)
  (:require [circle.backend.build.inference :as inference])
  (:require [circle.backend.build.test-utils :as test])
  (:require [circle.backend.git :as git])
  (:require fs))

(fact "Inferred setup actions for a php repo include initializing pear and installing phpunit"
      (let [repo (test/test-repo "simple_php")
            inferred-setup-actions (filter #(= (:type %) :setup)
                                           (inference/infer-actions* :php repo))
            action-names (->> inferred-setup-actions (map :name) (into #{}))]
        action-names => (contains "pear initialization")
        action-names => (contains "install phpunit")))

(fact "The default test command is `phpunit test/` from the repo directory"
      (let [repo (test/test-repo "simple_php")
            inferred-test-actions (filter #(= (:type %) :test)
                                          (inference/infer-actions* :php repo))
            action-names (->> inferred-test-actions (map :name) (into #{}))]
        action-names => (contains "PHPUnit")))

