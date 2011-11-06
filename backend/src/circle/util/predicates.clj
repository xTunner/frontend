(ns circle.util.predicates)

(defmacro instance-pred [name class]
  `(defn ~name [x#]
     (instance? ~class x#)))

(instance-pred bool? java.lang.Boolean)
(instance-pred ref? clojure.lang.IRef)
(instance-pred namespace? clojure.lang.Namespace)