(ns frontend.models.test-build
  (:require [frontend.models.build :as build-model]
            [cljs.test :refer-macros (deftest is testing)]))

;;(deftest status-badge-classes
;;  (testing "correct classes are mapped to build states"
;;    (are [state icon-name] (= icon-name
;;                              (build-model/status-class {:status state :outcome state}))
;;         "failed"               "fail"
;;         "timedout"             "fail"
;;         "no_tests"             "fail"
;;         "retried"              "stop"
;;         "canceled"             "stop"
;;         "infrastructure_fail"  "stop"
;;         "killed"               "stop"
;;         "not_run"              "stop"
;;         "success"              "pass"
;;         "running"              "busy"
;;         "queued"               "queued"
;;         "not_running"          "queued"
;;         "scheduled"            "queued")))
;;
;;(deftest status-badge-icons
;;  (testing "correct icons are mapped to build states"
;;    (are [state icon-name] (= icon-name
;;                              (build-model/status-icon {:status state :outcome state}))
;;         "failed"               "Status-Failed"
;;         "timedout"             "Status-Failed"
;;         "no_tests"             "Status-Failed"
;;         "retried"              "Status-Canceled"
;;         "canceled"             "Status-Canceled"
;;         "infrastructure_fail"  "Status-Canceled"
;;         "killed"               "Status-Canceled"
;;         "not_run"              "Status-Canceled"
;;         "success"              "Status-Passed"
;;         "running"              "Status-Running"
;;         "queued"               "Status-Queued"
;;         "not_running"          "Status-Queued"
;;         "scheduled"            "Status-Queued")))
