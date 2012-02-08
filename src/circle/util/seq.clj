(ns circle.util.seq
  "fns that work on seqs"
  (:use [arohner.utils :only (inspect)]))

(defn find-first
  "Returns the first item that passes predicate f"
  [f seq]
  (->> seq
       (filter f)
       (first)))

(defn vec-concat
  "Same as concat, but returns a vector"
  [& args]
  (->> args
       (apply concat)
       (into [])))

(defn indexed [coll]
  (map vector (range) coll))

(defn index-of
  "Given a coll, returns the index of the item that is = to val. O(n)"
  [coll val]
  (->> coll
       (indexed)
       (filter #(= val (second %)))
       (first)
       (first)))
