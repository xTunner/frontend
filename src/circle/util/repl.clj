(ns circle.util.repl)

(defn apropos [str-or-re]
  "Like clojure.repl/apropos, but prints the ns as well."
  [str-or-re]
  (let [matches? (if (instance? java.util.regex.Pattern str-or-re)
                   (fn match-re [v]
                     (re-find str-or-re (str v)))
                   (fn match-str [v]
                     (.contains (str v) (str str-or-re))))]
    (->> (all-ns)
         (mapcat ns-publics)
         (vals)
         (filter matches?))))

(defmacro inspect
  "prints the expression '<name> is <value>', and returns the value"
  [value]
  `(let [result# ~value]
     (println '~value "is" (with-out-str (clojure.pprint/pprint result#)))
     result#))