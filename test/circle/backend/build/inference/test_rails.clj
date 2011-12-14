(ns circle.backend.build.inference.test-rails
  (:use midje.sweet)
  (:use circle.backend.build.inference.rails)
  (:require [circle.backend.git :as git]))

(fact "bundler? and rspec? works"
  (let [travis-url "https://github.com/travis-ci/travis-ci"
        redmine-url "https://github.com/edavis10/redmine"]
    (git/ensure-repo travis-url)
    (git/ensure-repo redmine-url)

    (-> travis-url (git/default-repo-path) (bundler?)) => true
    (-> redmine-url (git/default-repo-path) (bundler?)) => false

    (-> travis-url (git/default-repo-path) (rspec?)) => true
    (-> redmine-url (git/default-repo-path) (rspec?)) => false))
