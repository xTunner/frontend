(ns circle.backend.test-nodes
  (:require [circle.backend.nodes :as nodes])
  (:use midje.sweet))

(facts "Our pallet compute service is configured properly"
  (pallet.compute/service :aws) => truthy
  (count (.listImages (pallet.compute/service :aws))) => pos?
  
  ;; it's possible there are no nodes in production, but this is very
  ;; unlikely.
  (count (circle.backend.nodes/all-nodes)) => pos?)

