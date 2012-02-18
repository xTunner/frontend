(ns circle.backend.build.inference.test-php
  (:use midje.sweet)
  (:use circle.backend.build.inference.php)
  (:require [circle.test-utils :as test])
  (:require [circle.backend.git :as git])
  (:require fs))

(fact "The default test command is `phpunit test/` from the repo directory"
      (let [repo (test/test-repo "simple_php")
            inferred-test-actions (spec repo)]
        inferred-test-actions => (contains #(= "Run PHPUnit" (:name %)))))

(fact "When phpunit is vendorized, the vendored phpunit command is used"
      (let [repo (test/test-repo "vendorized_phpunit")
           inferred-actions (spec repo)]
        inferred-actions
        => (contains #(re-find #".*/vendorized_phpunit/phpunit/phpunit"
                              (:bash-command %)))))

