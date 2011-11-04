(ns circle.util.test-args
  (:use midje.sweet)
  (:use circle.util.args))

(defn f [foo]
  (require-arg foo)
  foo)

(defn g [foo bar]
  (require-args foo bar)
  (+ foo bar))

(fact "require-arg works"
  (f 2) => 2
  (f nil) => (throws Exception "foo is required"))

(fact "require-args works"
  (g 1 2) => 3
  (g 1 nil) => (throws Exception "bar is required")
  (g nil 2) => (throws Exception "foo is required")
  (g nil nil) => (throws Exception))