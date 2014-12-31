(ns frontend.models.test-project
  (:require [frontend.models.project :as project])
  (:require-macros [cemerick.cljs.test :refer [is deftest with-test run-tests testing test-var]]))


(deftest project-name-works
  (is (= "org/repo" (project/project-name {:vcs_url "https://github.com/org/repo"})))
  (is (= "org/repo" (project/project-name {:vcs_url "https://ghe.github.com/org/repo"}))))
