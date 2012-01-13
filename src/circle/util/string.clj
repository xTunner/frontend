(ns circle.util.string)

(defn non-empty?
  [s]
  (> (.length s) 0))