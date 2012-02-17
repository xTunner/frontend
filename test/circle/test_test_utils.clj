(ns circle.backend.build.test-test-utils
  (:use circle.backend.build.test-utils)
  (:use midje.sweet))

(fact "localhost-ssh-map always returns a private key"
  (localhost-ssh-map) => (contains {:private-key string?}))