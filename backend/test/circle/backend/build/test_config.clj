(ns circle.backend.build.test-config
  (:use midje.sweet)
  (:use [circle.backend.build :only (checkout-dir)])
  (:use [circle.backend.project.circle :only (parse-action-map)])
  (:use [circle.backend.action.bash :only (bash)])
  (:use [circle.backend.build.utils :only (minimal-build)]))

(fact "parse-action-map works"
  (let [b (minimal-build)
        cmd {(keyword "lein daemon start \":web\"") {:environment {:CIRCLE_ENV "production", :SWANK "true"}}}]
    (parse-action-map b cmd) => truthy
    (provided
      (bash "lein daemon start \":web\"" :environment {:CIRCLE_ENV "production", :SWANK "true"} :pwd (checkout-dir b)) => truthy :times 1)))