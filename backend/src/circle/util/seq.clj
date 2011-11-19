(ns circle.util.seq
  "fns that work on seqs")

(defn find-first
  "Returns the first item that passes predicate f"
  [f seq]
  (->> seq
       (filter f)
       (first)))