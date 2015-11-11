(ns frontend.models.test-feature
  (:require [frontend.feature :as feature])
  (:require-macros [cemerick.cljs.test :refer [is deftest with-test run-tests testing test-var]]))

(deftest enabled?-works
  (is (not (feature/enabled? :foo)))
  (feature/enable-in-cookie :foo)
  (is (feature/enabled? :foo))
  (feature/disable-in-cookie :foo)
  (is (not (feature/enabled? :foo)))

  ;; test query string overrides
  (with-redefs [feature/set-in-query-string? (constantly true)
                feature/enabled-in-query-string? (constantly true)]
    ;; cookie set to false, query set to true
    (is (feature/enabled? :foo))
    (feature/enable-in-cookie :foo)
    (with-redefs [feature/enabled-in-query-string? (constantly false)]
      ;; cookie set to true, query set to false
      (is (not (feature/enabled? :foo)))))
  (feature/disable-in-cookie :foo))
