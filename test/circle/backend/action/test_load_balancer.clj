(ns circle.backend.action.test-load-balancer
  (:require [circle.backend.action.load-balancer :as lb-action])
  (:require [circle.backend.load-balancer :as lb])
  (:use [circle.backend.action :only (defaction abort!)])
  (:use midje.sweet)
  (:use [circle.backend.build.run :only (run-build)])
  (:require [clj-time.core :as time])
  (:use [circle.model.build :only (successful?)])
  (:use circle.test-utils))

(test-ns-setup)

(defaction successful-action [act-name]
  {:name act-name}
  (fn [build]
    nil))

(fact "load balancer calls abort when shit breaks"
  (let [lb-name "lb-bogus"
        instance-ids ["i-bogus"]
        build (minimal-build
               :instance-ids instance-ids
               :lb-name lb-name
               :actions [(successful-action "1")
                         (lb-action/wait-for-healthy)
                         (successful-action "2")])]

    (run-build build :cleanup-on-failure false) => build
    (provided
      (lb/healthy? anything anything) => false
      (abort! build anything) => anything :times 1
      (lb-action/lb-healthy-sleep) => (time/millis 1)
      (lb-action/lb-healthy-timeout) => (time/millis 30))))


