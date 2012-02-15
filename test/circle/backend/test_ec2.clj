(ns circle.backend.test-ec2
  (:use circle.backend.ec2)
  (:require [circle.backend.load-balancer :as lb])
  (:use [circle.util.except :only (throwf)])
  (:use midje.sweet))

(fact "safe terminate doesn't terminate instances attached to the load balancer"
  (safe-terminate "i-production") => nil
  (provided
    (terminate-instances! anything) => anything :times 0
    (lb/instances "www") => ["i-production"]))

(fact "safe terminate kills unattached instances"
  (safe-terminate "i-devel") => nil
  (provided
    (terminate-instances! "i-devel") => nil :times 1)
  (provided
    (lb/instances "www") => ["i-production"]))

(future-fact "block-until-running retries when instance returns nil"
  (block-until-running "i-bogus") => (throws Exception)
  (provided
    (instance anything) =streams=> [(throwf "not started") (throwf "not started") (throwf "not started") :running]))

