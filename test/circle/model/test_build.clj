(ns circle.model.test-build
  (:use midje.sweet)
  (:use [circle.backend.build.test-utils :only (minimal-build test-ns-setup)])
  (:use circle.model.build))

(test-ns-setup)

(fact "checkout-dir handles spaces"
  (let [b (minimal-build)]
    (checkout-dir b) => #"circle-dummy-project-\d+"))

(fact "ensure-project-id works"
  (let [b (minimal-build)]
    @b => (contains {:project_id truthy})))

(fact "load build works"
  (let [b1 (minimal-build)
        b2 (fetch-build (-> @b1 :_id))]
    (= @b1 @b2) => true))