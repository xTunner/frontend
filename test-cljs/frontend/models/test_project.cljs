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
    (is (= (project/show-build-timing? private-project (test-utils/example-plan :expired-trial)) false))
  (with-redefs [config/enterprise? (constantly true)]
    (is (= (project/show-build-timing? private-project (test-utils/example-plan :free)) true))))

(deftest test-show-upsell-works
  (with-redefs [config/enterprise? (constantly false)]
    (is (= (project/show-upsell? oss-project (test-utils/example-plan :free)) false))
    (is (= (project/show-upsell? private-project (test-utils/example-plan :free)) true))
    (is (= (project/show-upsell? private-project (test-utils/example-plan :paid)) false)))
    (is (= (project/show-upsell? private-project (test-utils/example-plan :osx)) false))
    (is (= (project/show-upsell? private-project (test-utils/example-plan :trial)) false))
    (is (= (project/show-upsell? private-project (test-utils/example-plan :expired-trial)) true))
  (with-redefs [config/enterprise? (constantly true)]
    (is (= (project/show-upsell? private-project (test-utils/example-plan :free)) false))))

(deftest test-add-show-insights-works
   (with-redefs [config/enterprise? (constantly false)]
    (is (= (:show-insights? (project/add-show-insights? oss-project test-utils/example-user-plans-free) true)))
    (is (= (:show-insights? (project/add-show-insights? private-project test-utils/example-user-plans-free) false)))
    (is (= (:show-insights? (project/add-show-insights? private-project test-utils/example-user-plans-paid) true)))
    (is (= (:show-insights? (project/add-show-insights? private-project test-utils/example-user-plans-piggieback)) true)))
    (is (= (:show-insights? (project/add-show-insights? private-project test-utils/example-user-plans-trial)) true))
    (is (= (:show-insights? (project/add-show-insights? private-project test-utils/example-user-plans-expired-trial)) false))
  (with-redefs [config/enterprise? (constantly true)]
    (is (= (:show-insights? (project/add-show-insights? private-project test-utils/example-user-plans-free) true)))))
