(ns frontend.models.test-build
  (:require [frontend.models.build :as build-model]
            [cljs.test :refer-macros [deftest is testing are]]))

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
         "not_running"          "queued"
         "scheduled"            "queued")))

(deftest status-badge-icons
  (testing "correct icons are mapped to build states"
    (are [state icon-name] (= icon-name
                              (build-model/status-icon {:status state :outcome state}))
         "failed"               "Status-Failed"
         "timedout"             "Status-Failed"
         "no_tests"             "Status-Failed"
         "retried"              "Status-Canceled"
         "canceled"             "Status-Canceled"
         "infrastructure_fail"  "Status-Canceled"
         "killed"               "Status-Canceled"
         "not_run"              "Status-Canceled"
         "success"              "Status-Passed"
         "running"              "Status-Running"
         "queued"               "Status-Queued"
         "not_running"          "Status-Queued"
         "scheduled"            "Status-Queued")))

(deftest containers-test
  (testing "should not modify complete containers"
    (let [build {:parallel 2
                 :steps [{:actions [{:index 0 :step 0} {:index 1 :step 0}]}
                         {:actions [{:index 0 :step 1} {:index 1 :step 1}]}]}]
      
      (is (= [{:index 0 :actions [{:index 0 :step 0} {:index 0 :step 1}]}
              {:index 1 :actions [{:index 1 :step 0} {:index 1 :step 1}]}]
             (build-model/containers build)))))
  
  (testing "should add dummy step for containers without some steps"
    (let [build {:parallel 2
                 :steps [{:actions [{:index 0 :step 0} {:index 1 :step 0}]}
                         {:actions [{:index 0 :step 1}]}
                         {:actions [{:index 0 :step 2} {:index 1 :step 2}]}]}]
      
      (is (= [{:index 0 :actions [{:index 0 :step 0} {:index 0 :step 1} {:index 0 :step 2}]}
              {:index 1 :actions [{:index 1 :step 0} {:index 1 :step 1 :status "running" :filler-action true} {:index 1 :step 2}]}]
             (build-model/containers build)))))

  (testing "should replicate not parallel steps"
    (let [build {:parallel 2
                 :steps [{:actions [{:index 0 :step 0} {:index 1 :step 0}]}
                         {:actions [{:index 0 :step 1 :parallel false}]}
                         {:actions [{:index 0 :step 2} {:index 1 :step 2}]}]}]
      
      (is (= [{:index 0 :actions [{:index 0 :step 0} {:index 0 :step 1 :parallel false} {:index 0 :step 2}]}
              {:index 1 :actions [{:index 1 :step 0} {:index 0 :step 1 :parallel false} {:index 1 :step 2}]}]
             (build-model/containers build)))))

  (testing "should fill skipped steps"
    (let [build {:parallel 2
                 :steps [{:actions [{:index 0 :step 0} {:index 1 :step 0}]}
                         {:actions [{:index 0 :step 2} {:index 1 :step 2}]}]}]
      
      (is (= [{:index 0 :actions [{:index 0 :step 0} {:index 0 :step 1 :status "running" :filler-action true} {:index 0 :step 2}]}
              {:index 1 :actions [{:index 1 :step 0} {:index 1 :step 1 :status "running" :filler-action true} {:index 1 :step 2}]}]
             (build-model/containers build))))))
