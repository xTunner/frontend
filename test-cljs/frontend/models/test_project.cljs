(ns frontend.models.test-project
  (:require [frontend.models.project :as project]
            [frontend.test-utils :as test-utils]
            [frontend.config :as config])
  (:require-macros [cemerick.cljs.test :refer [is deftest with-test run-tests testing test-var]]))

(def oss-project
  {:oss true
   :vcs_url "https://github.com/circleci/circle"})

(def private-project
  {:oss false
   :vcs_url "https://github.com/circleci/circle"})

(deftest project-name-works
  (is (= "org/repo" (project/project-name {:vcs_url "https://github.com/org/repo"})))
  (is (= "org/repo" (project/project-name {:vcs_url "https://ghe.github.com/org/repo"}))))

(deftest test-show-build-timing-works
  (with-redefs [config/enterprise? (constantly false)]
    (is (= (project/show-build-timing? oss-project (test-utils/example-plan :free)) true))
    (is (= (project/show-build-timing? private-project (test-utils/example-plan :free)) false))
    (is (= (project/show-build-timing? private-project (test-utils/example-plan :paid)) true)))
    (is (= (project/show-build-timing? private-project (test-utils/example-plan :osx)) true))
    (is (= (project/show-build-timing? private-project (test-utils/example-plan :trial)) true))
  (with-redefs [config/enterprise? (constantly true)]
    (is (= (project/show-build-timing? private-project (test-utils/example-plan :free)) true))))

(deftest test-show-insights-works
   (with-redefs [config/enterprise? (constantly false)]
     (is (= (project/show-insights? test-utils/example-user-plans-free oss-project) true))
     (is (= (project/show-insights? test-utils/example-user-plans-free private-project) false))
     (is (= (project/show-insights? test-utils/example-user-plans-paid private-project) true))
     (is (= (project/show-insights? test-utils/example-user-plans-piggieback private-project) true)))
  (with-redefs [config/enterprise? (constantly true)]
    (is (= (project/show-insights? test-utils/example-user-plans-free private-project) true))))
