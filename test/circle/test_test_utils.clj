(ns circle.test-test-utils
  (:use circle.test-utils)
  (:use midje.sweet))

(fact "localhost-ssh-map always returns a private key"
  (localhost-ssh-map) => (contains {:private-key string?}))