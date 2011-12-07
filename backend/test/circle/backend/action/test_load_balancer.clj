(ns circle.backend.action.test-load-balancer
  (:require [circle.backend.action.load-balancer :as lb-action])
  (:require [circle.backend.load-balancer :as lb])
  (:use [circle.backend.action :only (defaction)])
  (:use midje.sweet)
  (:use [circle.backend.build.run :only (run-build)])
  (:use [circle.backend.build :only (successful?)])
  (:use circle.backend.build.test-utils))

(defaction successful-action [act-name]
  {:name act-name}
  (fn [build]
    nil))

(fact "load balancer calls abort when shit breaks"
  (against-background
    (lb/healthy? anything anything) => false
    (lb-action/lb-healthy-retries) => 2)
  (let [lb-name "lb-bogus"
        instance-ids ["i-bogus"]
        build (minimal-build :project-name "lb wait for healthy test"
                 :instance-ids instance-ids
                 :lb-name lb-name
                 :actions [(successful-action "1")
                           (lb-action/wait-for-healthy)
                           (successful-action "2")])]
    
    (run-build build :cleanup-on-failure false)
    (successful? build) => falsey))

(fact "stubbing works"
  (lb/healthy? "lb-bogus" ["i-foo"]) => false
  (provided
    (lb/healthy? "lb-bogus" ["i-bogus"]) => false))

(defn bogus [x] x)

(fact "stubbing works"
  (bogus "foo") => "foo"
  (provided
    (bogus :bar) => "foo"))

(fact "provided can stub invisible fns"
  (circle.bogus/bar 3) => 10
  (provided (circle.bogus/foo 3) => 5))

(fact "provided can stub apply'd fns"
  (circle.bogus/bar* 3) => 10
  (provided
    (apply circle.bogus/foo [3]) => 5))