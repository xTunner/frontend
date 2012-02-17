(ns circle.util.ns
  "fns for working with clojure namespaces")

(defn destroy-ns
  "TODO"
  [ns]
  (doseq [sym (keys (ns-refers ns))]
    (ns-unmap ns sym))
  (doseq [a (keys (ns-aliases ns))]
    (ns-unalias ns a)))

(defmacro with-ns
  "Evaluates body in another namespace.  ns is either a namespace
  object or a symbol.  This makes it possible to define functions in
  namespaces other than the current one."
  [ns & body]
  `(binding [*ns* (the-ns ~ns)]
     ~@(map (fn [form] `(eval '~form)) body)))