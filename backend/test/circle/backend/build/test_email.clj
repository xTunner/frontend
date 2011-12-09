(ns circle.backend.build.test-email
  (:use midje.sweet)
  (:require [circle.backend.build.test-utils :as test])
  (:use [circle.backend.build.test-utils :only (minimal-build)])
  (:require [circle.backend.build.email :as email])
  (:use [circle.backend.action :only (defaction)])
  (:require [circle.backend.build :as build])
  (:use [circle.backend.build.run :only (run-build)])
  (:use circle.backend.build.config))

(defaction successful-action [act-name]
  {:name act-name}
  (fn [build]
    nil))

(defn successful-build []
  (build/build {:project_name "succesful build"
                :build_num 1
                :vcs_url "git@github.com:foo/bar.git"
                :vcs_revision "f00b4r"
                :actions [(successful-action "1")
                          (successful-action "2")
                          (successful-action "3")]}))

(fact "notify email works"
  (build/successful? (run-build (minimal-build :actions [(successful-action "1")]
                                               :notify_emails ["foo@bar.com"
                                                              "baz@bar.com"]))) => truthy
  (provided
    (circle.backend.email/send :to "foo@bar.com" :subject anything :body anything) => anything :times 1
    (circle.backend.email/send :to "baz@bar.com" :subject anything :body anything) => anything :times 1))