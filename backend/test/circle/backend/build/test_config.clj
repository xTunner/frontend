(ns circle.backend.build.test-config
  (:use midje.sweet)
  (:use circle.backend.build.config)
  (:use [circle.backend.build :only (checkout-dir valid? validate)])
  (:use [circle.backend.action.bash :only (bash)])
  (:require [circle.backend.build.test-utils :as test])
  (:use [circle.util.predicates :only (ref?)]))

(test/ensure-circle-project)

(fact "parse-action-map works"
  (let [b (test/minimal-build)
        dir (checkout-dir b)
        cmd {(keyword "lein daemon start \":web\"") {:environment {:CIRCLE_ENV "production", :SWANK "true"}}}]
    (parse-action-map dir cmd) => truthy
    (provided
      (bash "lein daemon start \":web\"" :environment {:CIRCLE_ENV "production", :SWANK "true"} :pwd dir) => truthy :times 1)))

(fact "load-job works"
  (load-job test/circle-config :build) => truthy)

(fact "template/find works keyword name"
  (circle.backend.build.template/find :build) => truthy)

(fact "template/find works with strings"
  (circle.backend.build.template/find "build") => truthy)

(fact "build-from-config works"
  (let [project test/circle-project
        config (get-config-for-url (-> test/circle-project :vcs_url))
        checkout-dir (checkout-dir (-> project :name) 1)
        vcs-revision "9538736fc7e853db8dac3a6d2f35d6dcad8ec917"
        b (build-from-config config test/circle-project
                             :vcs-revision vcs-revision
                             :job-name :build
                             :build-num 1
                             :checkout-dir checkout-dir)]
    (ref? b) => true
    (-> @b :vcs-revision) => "9538736fc7e853db8dac3a6d2f35d6dcad8ec917"
    (validate @b) => nil))

(fact "build-from-json works"
  (let [build (build-from-json test/circle-github-json)]
    (ref? build) => true
    (-> @build :vcs-revision) => "9538736fc7e853db8dac3a6d2f35d6dcad8ec917"))

(fact "build loads the node and slurps the ssh keys"
  ;; The circle.yml contains :private-key, :public-key. Verify they were slurped.
  (let [build (build-from-json test/circle-github-json)]
    (-> @build :node) => map?
    (-> @build :node :username) => "ubuntu"
    (-> @build :node :public-key) => #"^ssh-rsa"
    (-> @build :node :private-key) => #"BEGIN RSA PRIVATE KEY"
    (validate @build) => nil))

(tabular
 (fact "build-from-url works"
   (infer-build-from-url ?url) => ref?)
 ?url
 "https://github.com/travis-ci/travis-ci"
 "https://github.com/arohner/CircleCI"
 "https://github.com/edavis10/redmine")

(fact "build email addresses are correct"
  (let [build (build-from-json test/circle-github-json)]
    (-> @build :notify_emails) => #{"arohner@gmail.com"}))

(tabular
 (fact "infer project name works"
   (infer-project-name ?url) => ?expected)
 ?url ?expected
 "https://github.com/rails/rails.git" "rails")