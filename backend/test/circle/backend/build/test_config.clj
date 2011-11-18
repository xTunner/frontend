(ns circle.backend.build.test-config
  (:use midje.sweet)
  (:use circle.backend.build.config)
  (:use [circle.backend.build :only (checkout-dir)])
  (:use [circle.backend.action.bash :only (bash)])
  (:use [circle.backend.build.utils :only (minimal-build circle-config circle-project circle-github-json)])
  (:use [circle.util.predicates :only (ref?)]))

(fact "parse-action-map works"
  (let [b (minimal-build)
        dir (checkout-dir b)
        cmd {(keyword "lein daemon start \":web\"") {:environment {:CIRCLE_ENV "production", :SWANK "true"}}}]
    (parse-action-map dir cmd) => truthy
    (provided
      (bash "lein daemon start \":web\"" :environment {:CIRCLE_ENV "production", :SWANK "true"} :pwd dir) => truthy :times 1)))

(fact "load-job works"
  (load-job circle-config :build) => truthy)

(fact "template/find works keyword name"
  (circle.backend.build.template/find :build) => truthy)

(fact "template/find works with strings"
  (circle.backend.build.template/find "build") => truthy)

(fact "build-from-config works"
  (let [project circle-project
        github-json circle-github-json
        config circle-config
        checkout-dir "test-proj-1"
        b (build-from-config circle-config circle-github-json circle-project :build 1 checkout-dir)]
    (ref? b) => true))