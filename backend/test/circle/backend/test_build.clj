(ns circle.backend.test-build
  (:use midje.sweet)
  (:use [circle.backend.build.utils :only (minimal-build)])
  (:use circle.backend.build))

(fact "checkout-dir handles spaces"
  (let [b (minimal-build :project-name "test proj"
                         :build-num 42)]
    (checkout-dir b) => #"testproj-42"))