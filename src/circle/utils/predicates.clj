(ns circle.utils.predicates)

(defn bool?
  "returns true if x is a bool"
  [x]
  (instance? java.lang.Boolean x))

(defn ref? [x]
  (instance? clojure.lang.IRef x))