(ns circle.backend.test-build
  (:use midje.sweet)
  (:use [circle.backend.build.test-utils :only (minimal-build)])
  (:use circle.backend.build))

(fact "checkout-dir handles spaces"
  (let [b (minimal-build :project-name "test proj"
                         :build-num 42)]
    (checkout-dir b) => #"testproj-42"))

(fact "ensure-project-id works"
  (let [b (minimal-build)]
    (ensure-project-id b)
    @b => (contains {:_project-id truthy})))