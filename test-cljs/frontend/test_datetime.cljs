(ns frontend.test-datetime
  (:require [frontend.datetime :as datetime])
  (:require-macros [cemerick.cljs.test :refer (is deftest testing)]))

(deftest milli-to-float-duration-works
  (testing "basic"
    (is (= (first (datetime/millis-to-float-duration 100))
           "100.0ms")))
  (testing "unit detection"
    (is (= (first (datetime/millis-to-float-duration 6000))
           "6.0s")))
  (testing "huge millis"
    (is (= (first (datetime/millis-to-float-duration 60000000))
           "16.7h")))
  (testing "tiny millis"
    (is (= (first (datetime/millis-to-float-duration 0.6))
           "0.6ms")))
  (testing "less greater or equal to 1 should stay at smaller unit"
    (is (= (first (datetime/millis-to-float-duration 1000))
           "1.0s")))
  (testing "zero"
    (is (= (first (datetime/millis-to-float-duration 0))
           "0")))
  (testing "accepts options"
    (is (= (first (datetime/millis-to-float-duration 60000000
                                                     {:unit :seconds
                                                      :decimals 0}))
           "60000s")))
  (testing "returns unit-hash"
    (is (= (-> (datetime/millis-to-float-duration 60000000)
               (last)
               (:unit))
           :hours))))


(deftest nice-floor-duration-works
  (testing "basic"
    (let [result (datetime/nice-floor-duration 6600)
          [result-str] (datetime/millis-to-float-duration result)]
      (is (= result-str
             "6.0s")))))

(deftest format-duration-works
  (testing "full units"
    (testing "singular"
      (is (= (datetime/time-ago 1000) "1 second")))
    (testing "plural"
      (is (= (datetime/time-ago 480000) "8 minutes"))))
  (testing "abbreviated  units"
    (testing "singular"
      (is (= (datetime/time-ago 3600000) "1 hour")))
    (testing "plural"
      (is (= (datetime/time-ago (* 3600000 48)) "2 days")))))
