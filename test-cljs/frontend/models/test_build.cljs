(ns frontend.models.test-build
  (:require [cemerick.cljs.test :as t]
            [frontend.models.build :as build-model])
  (:require-macros [cemerick.cljs.test :refer (deftest are testing)]))

(deftest status-badge-classes
  (testing "correct classes are mapped to build states"
    (are [state icon-name] (= icon-name
                              (build-model/status-class {:status state :outcome state}))
         "failed"               "fail"
         "timedout"             "fail"
         "no_tests"             "fail"
         "retried"              "stop"
         "canceled"             "stop"
         "infrastructure_fail"  "stop"
         "killed"               "stop"
         "not_run"              "stop"
         "success"              "pass"
         "running"              "busy"
         "queued"               "queued"
         "not_running"          "queued")))

(deftest status-badge-icons
  (testing "correct icons are mapped to build states"
    (are [state icon-name] (= icon-name
                              (build-model/status-icon-v2 {:status state :outcome state}))
         "failed"               "Status-Failed"
         "timedout"             "Status-Failed"
         "no_tests"             "Status-Failed"
         "retried"              "Status-Cancelled"
         "canceled"             "Status-Cancelled"
         "infrastructure_fail"  "Status-Cancelled"
         "killed"               "Status-Cancelled"
         "not_run"              "Status-Cancelled"
         "success"              "Status-Passed"
         "running"              "Status-Running"
         "queued"               "Status-Queued"
         "not_running"          "Status-Queued")))
