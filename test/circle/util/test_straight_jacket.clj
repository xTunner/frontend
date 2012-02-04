(ns circle.util.test-straight-jacket
  (:use midje.sweet)
  (:use circle.util.straight-jacket)
  (:require [circle.airbrake :as airbrake]))

(fact "straight-jacket catches exceptions"
  (straight-jacket (throw (Exception. "test"))) => nil
  (provided
    (airbrake/airbrake anything anything anything anything) => nil
    (circle.env/env) => :production))



(fact "except in test mode"
  (straight-jacket (/ 1 0)) => (throws ArithmeticException)
  (provided
    (circle.env/env) => :test))

(fact "and dev mode"
  (straight-jacket (/ 1 0)) => (throws ArithmeticException)
  (provided
    (circle.env/env) => :development))


(fact "code still works in a straight-jacket"
  (straight-jacket (+ 4 5)) => 9)


(fact "straight-jacket catches assertions"
  (straight-jacket (assert false)) => nil
  (provided
    (airbrake/airbrake anything anything anything anything) => nil
    (circle.env/env) => :production))