(ns circle.test-workers
  (:use midje.sweet)
  (:use circle.workers))

(fact "log-future returns futures"
  ;; A normal future will return a future object here, even if the
  ;; code obviously throws an exception. Make sure that's the case
  ;; here as well.
  (log-future (/ 1 0)) => future?)

(fact "log-future works"
  @(log-future (apply + [1 1])) => 2)