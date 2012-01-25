(ns circle.backend.test-build
  (:use midje.sweet)
  (:use [circle.backend.build.test-utils :only (minimal-build)])
  (:use circle.model.build))

(fact "checkout-dir handles spaces"
  (let [b (minimal-build :build_num 42)]
    (checkout-dir b) => "Dummy-Project-42"))

(fact "ensure-project-id works"
  (let [b (minimal-build)]
    @b => (contains {:_project_id truthy})))