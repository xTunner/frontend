(ns circle.backend.test-build
  (:use midje.sweet)
  (:use [circle.backend.build.test-utils :only (minimal-build)])
  (:use circle.model.build))

(fact "checkout-dir handles spaces"
  (let [b (minimal-build)]
    (checkout-dir b) => #"Dummy-Project-\d+"))

(fact "ensure-project-id works"
  (let [b (minimal-build)]
    @b => (contains {:_project_id truthy})))