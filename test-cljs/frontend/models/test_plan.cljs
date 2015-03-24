(ns frontend.models.test-plan
  (:require [cemerick.cljs.test :as t]
            [frontend.test-utils :refer (example-plan)]
            [frontend.models.plan :as pm])
  (:require-macros [cemerick.cljs.test :refer [is deftest with-test run-tests testing test-var]]))

(deftest grandfathered-works
  (is (pm/grandfathered? (example-plan :grandfathered)))
  (is (pm/grandfathered? (example-plan :grandfathered :trial)))
  (is (pm/grandfathered? (example-plan :grandfathered :free)))
  (is (pm/grandfathered? (example-plan :grandfathered :free :trial)))
  (is (not (pm/grandfathered? (example-plan :trial))))
  (is (not (pm/grandfathered? (example-plan :free))))
  (is (not (pm/grandfathered? (example-plan :paid))))
  (is (not (pm/grandfathered? (example-plan :big-paid))))
  (is (not (pm/grandfathered? (example-plan :trial :free))))
  (is (not (pm/grandfathered? (example-plan :trial :paid))))
  (is (not (pm/grandfathered? (example-plan :trial :big-paid))))
  (is (not (pm/grandfathered? (example-plan :free :paid))))
  (is (not (pm/grandfathered? (example-plan :free :big-paid))))
  (is (not (pm/grandfathered? (example-plan :trial :free :paid)))))
