(ns circle.backend.build.inference.test-php
  (:use midje.sweet)
  (:use circle.backend.build.inference.php)
  (:require [circle.backend.build.inference :as inference])
  (:require [circle.backend.build.test-utils :as test])
  (:require [circle.backend.git :as git])
  (:require fs))

(fact "Inferred actions for a php repo include installing phpunit"
      (let [repo (test/test-repo "simple_php")
            inferred-actions (inference/infer-actions* :php repo)
            action-names (->> inferred-actions (map :name) (into #{}))]
        action-names => (contains "pear initialization")
        action-names => (contains "install phpunit")))

(future-fact "The default test command is `phpunit test/` from the repo directory")

(future-fact "phpunit tests are executed as a :test action")

