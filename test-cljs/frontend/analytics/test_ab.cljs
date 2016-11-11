(ns frontend.analytics.test-ab
  (:require  [clojure.test :refer-macros [is deftest]]
             [frontend.analytics.ab :as ab]
             [frontend.models.feature :as feature]))

(deftest ab-test-treatments-works
  (with-redefs [feature/ab-test-treatment (partial get {:x :yes-x
                                                        :y :no-y})]
    (is (= {:x "yes-x" :y "no-y"}
           (ab/ab-test-treatments {:x true :y false})))))
