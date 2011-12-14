(ns circle.util.coerce
  "Functions that attempt to coerce arguments from one type to another"
  (:use [circle.util.core :only (apply-if)])
  (:use [circle.util.predicates :only (named?)]))

(defn to-name
  "Returns x, or (name x) if supported"
  [x]
  (apply-if (named? x) name x))


