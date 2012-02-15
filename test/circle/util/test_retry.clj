(ns circle.util.test-retry
  (:use midje.sweet)
  (:use circle.util.retry)
  (:use [circle.util.time :only (to-millis)]))

(defn foo [])

(fact "wait-for retries"
  (wait-for {:sleep 1
             :tries 10}
            (fn []
              (foo))) => true
              (provided
                (foo) =streams=> [false false false false true] :times 5))

(fact "wait-for throws on timeout"
  (wait-for {:sleep 1
             :tries 10}
            (fn []
              (foo))) => (throws Exception)
              (provided
                (foo) => false :times 10))

(fact "wait-for works with an error hook"
  (wait-for {:sleep 1
             :tries 10
             :error-hook (fn [e]
                           (printf (format "caught: %s\n" (.getMessage e))))}
            (fn []
              (foo))) => true
              (provided
                (foo) =streams=> [false false false false true] :times 5))

(fact "wait-for works with a success fn"
  (wait-for {:sleep 1
             :tries 10
             :success-fn (fn [v]
                           (= v 8))}
            (fn []
              (foo))) => 8
              (provided
                (foo) =streams=> (range) :times 9))



