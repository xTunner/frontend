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
  (testing "specify unit"
    (is (= (first (datetime/millis-to-float-duration 60000000
                                                     :seconds))
           "60000.0s")))
  (testing "returns unit-hash"
    (is (= (-> (datetime/millis-to-float-duration 60000000)
               (last))
           :hours))))
