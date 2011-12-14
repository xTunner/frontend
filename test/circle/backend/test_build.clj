(ns circle.backend.test-build
  (:use midje.sweet)
  (:use [circle.backend.build.test-utils :only (minimal-build)])
  (:use circle.backend.build))

(fact "checkout-dir handles spaces"
  (let [b (minimal-build :project_name "test proj"
                         :build_num 42)]
    (checkout-dir b) => #"testproj-42"))

(fact "ensure-project-id works"
  (let [b (minimal-build)]
    @b => (contains {:_project_id truthy})))