(ns circle.util.test-retry
  (:use midje.sweet)
  (:use circle.util.retry)
  (:use [circle.util.time :only (to-millis)]))

(defn foo [])

(fact "wait-for retries"
  (wait-for {:sleep 100
             :tries 10}
            (fn []
              (foo))) => true
              (provided
                (foo) =streams=> [false false false false true] :times 5))

(fact "wait-for throws on timeout"
  (wait-for {:sleep 100
             :tries 10}
            (fn []
              (foo))) => (throws Exception)
              (provided
                (foo) => false :times 10))

