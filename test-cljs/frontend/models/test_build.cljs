(ns frontend.models.test-build
  (:require [frontend.models.build :as build-model]
            [cljs.test :refer-macros (deftest is testing)]))

(deftest status-badge-classes
  (testing "correct classes are mapped to build states"
    (= "fail" (build-model/status-class {:status "failed" :outcome "failed"}))
    (= "fail" (build-model/status-class {:status "timedout" :outcome "timedout"}))
    (= "fail" (build-model/status-class {:status "no_tests" :outcome "no_tests"}))
    (= "stop" (build-model/status-class {:status "retried" :outcome "retried"}))
    (= "stop" (build-model/status-class {:status "canceled" :outcome "canceled"}))
    (= "stop" (build-model/status-class {:status "infrastructure_fail" :outcome "infrastructure_fail"}))
    (= "stop" (build-model/status-class {:status "killed" :outcome "killed"}))
    (= "stop" (build-model/status-class {:status "not_run" :outcome "not_run"}))
    (= "pass" (build-model/status-class {:status "success" :outcome "success"}))
    (= "busy" (build-model/status-class {:status "running" :outcome "running"}))
    (= "queued" (build-model/status-class {:status "queued" :outcome "queued"}))
    (= "queued" (build-model/status-class {:status "not_running" :outcome "not_running"}))
    (= "queued" (build-model/status-class {:status "scheduled" :outcome "scheduled"}))))

(deftest status-badge-icons
  (testing "correct icons are mapped to build states"
    (= "Status-Failed" (build-model/status-icon {:status "failed" :outcome "failed"}))
    (= "Status-Failed" (build-model/status-icon {:status "timedout" :outcome "timedout"}))
    (= "Status-Failed" (build-model/status-icon {:status "no_tests" :outcome "no_tests"}))
    (= "Status-Canceled" (build-model/status-icon {:status "retried" :outcome "retried"}))
    (= "Status-Canceled" (build-model/status-icon {:status "canceled" :outcome "canceled"}))
    (= "Status-Canceled" (build-model/status-icon {:status "infrastructure_fail" :outcome "infrastructure_fail"}))
    (= "Status-Canceled" (build-model/status-icon {:status "killed" :outcome "killed"}))
    (= "Status-Canceled" (build-model/status-icon {:status "not_run" :outcome "not_run"}))
    (= "Status-Passed" (build-model/status-icon {:status "success" :outcome "success"}))
    (= "Status-Running" (build-model/status-icon {:status "running" :outcome "running"}))
    (= "Status-Queued" (build-model/status-icon {:status "queued" :outcome "queued"}))
    (= "Status-Queued" (build-model/status-icon {:status "not_running" :outcome "not_running"}))
    (= "Status-Queued" (build-model/status-icon {:status "scheduled" :outcome "scheduled"}))))
