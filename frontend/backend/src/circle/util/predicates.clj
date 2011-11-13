(ns circle.util.predicates
  (:use [circle.util.core :only (seq1)]))

(defmacro instance-pred [name class]
  `(defn ~name [x#]
     (instance? ~class x#)))

(defn or-preds
  "Takes a seq of predicate functions. Returns a new predicate that 'or's  "
  [& ps]
  (fn [x]
    (->> ps
         (seq1)
         (map #(% x))
         (filter identity)
         (first)
         ((fn [result]
           (if result
             result
             false))))))

(instance-pred bool? java.lang.Boolean)
(instance-pred ref? clojure.lang.IRef)
(instance-pred namespace? clojure.lang.Namespace)
(instance-pred named-strict? clojure.lang.Named)

(def named?
  ^{:doc "Returns true if (name x) will work"} 
  (or-preds string? named-strict?))