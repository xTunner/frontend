(ns frontend.test-macros
  (:require [cemerick.cljs.test :refer [is]]))

(defmacro is-re [regex string]
  `(is (re-find ~regex ~string)))
