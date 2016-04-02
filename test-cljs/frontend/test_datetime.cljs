(ns frontend.test-datetime
  (:require [frontend.datetime :as datetime]
            [cljs.test :refer-macros [is deftest testing]]))

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
    (= (datetime/time-ago 1000) "1 second")
    (= (datetime/time-ago 2000) "2 seconds")
    (= (datetime/time-ago (* 60 1000)) "1 minute")
    (= (datetime/time-ago (* 60 2000)) "2 minutes")
    (= (datetime/time-ago (* 60 60 1000)) "1 hour")
    (= (datetime/time-ago (* 60 60 2000)) "2 hours")
    (= (datetime/time-ago (* 24 60 60 1000)) "1 day")
    (= (datetime/time-ago (* 24 60 60 2000)) "2 days")
    (= (datetime/time-ago (* 30 24 60 60 1000)) "1 month")
    (= (datetime/time-ago (* 30 24 60 60 2000)) "2 months")
    (= (datetime/time-ago (* 12 30 24 60 60 1000)) "1 year")
    (= (datetime/time-ago (* 12 30 24 60 60 2000)) "2 years"))
  (testing "abbreviated units"
    (= (datetime/time-ago-abbreviated 1000) "1 sec")
    (= (datetime/time-ago-abbreviated 2000) "2 sec")
    (= (datetime/time-ago-abbreviated (* 60 1000)) "1 min")
    (= (datetime/time-ago-abbreviated (* 60 2000)) "2 min")
    (= (datetime/time-ago-abbreviated (* 60 60 1000)) "1 hr")
    (= (datetime/time-ago-abbreviated (* 60 60 2000)) "2 hr")
    (= (datetime/time-ago-abbreviated (* 24 60 60 1000)) "1 day")
    (= (datetime/time-ago-abbreviated (* 24 60 60 2000)) "2 days")
    (= (datetime/time-ago-abbreviated (* 30 24 60 60 1000)) "1 month")
    (= (datetime/time-ago-abbreviated (* 30 24 60 60 2000)) "2 months")
    (= (datetime/time-ago-abbreviated (* 12 30 24 60 60 1000)) "1 year")
    (= (datetime/time-ago-abbreviated (* 12 30 24 60 60 2000)) "2 years")))
