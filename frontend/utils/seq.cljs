(ns frontend.utils.seq)

(defn find-index
  "Finds index of first item in coll that returns truthy for filter-fn"
  [filter-fn coll]
  (first (keep-indexed (fn [i x] (when (filter-fn x) i)) coll)))
