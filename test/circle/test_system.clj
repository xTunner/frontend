(ns circle.test-system
  (:use midje.sweet)
  (:require [circle.backend.ec2 :as ec2])
  (:use [circle.backend.action :only (action)])
  (:use [circle.backend.build.run :only (run-build)])
  (:use [circle.backend.build :only (successful?)])
  (:use [circle.backend.build.test-utils :only (minimal-build)])
  (:use circle.system))

(fact "graceful shutdown calls ec2/terminate when there are no more builds"
  (against-background
    (circle.backend.ec2/self-instance-id) => "i-bogus"
    (circle.backend.ec2/terminate-instances! "i-bogus") => anything :times 1)

  (let [build (minimal-build :actions [(action :name "sleep" :act-fn (fn [build] (Thread/sleep 1000)))])
        fut (future (run-build build))]
    (graceful-shutdown) => anything
    @fut => anything
    (successful? build) => true))