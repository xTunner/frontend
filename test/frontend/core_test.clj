(ns frontend.core-test
  (:require [clojure.test :refer :all]
            [frontend.core :refer :all]))

(deftest a-test
  (testing "IGNOREME, I pass."
    (is (= 1 1))))

(deftest backend-fallback-works
  (is (= {:host "staging.circleci.com" :proto "https"}
         (backend-fallback "staging.circlehost")))
  (is (= {:host "foo.circleci.com" :proto "https"}
         (backend-fallback "foo.circlehost"))))
