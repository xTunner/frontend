(ns circle.evil
  "nothing to see here, move along"
  (:use [clojure.contrib.with-ns :only (with-ns)])
  (:require circle.util.repl))

(with-ns 'clojure.core
  (defmacro inspect [value]
    `(circle.util.repl/inspect ~value)))