(ns circle.util.validation
  (:use [circle.util.except :only (throwf throw-if throw-if-not)])
  (:use [circle.util.predicates :only (namespace?)])
  (:use [circle.util.macro :only (decompose-defn-args* defn-map* defn-map)]))

(defn validate-dispatch [tag val]
  tag)

(defmulti validate "Dispatch method for validating. Takes two
arguments, the tag to dispatch on, and the value. defmethod this with
a namespaced keyword, then use validate-defn or validate-ns. The
method should return false or throw on failure." validate-dispatch)

(defn validate-symbol [x-name x-val tag]
  (if (namespace tag)
    (if (get-method validate tag)
      (validate tag x-val)
      (println "warning, no validation dispatch found for" tag "on" x-name))
    (println "warning, ignoring non-namespaced tag on" x-name)))

(defmacro defn-v
  "Same as defn, but if an argument is tagged, ^Foo x, run validate multimethod on the argument first."
  [& args]
  (let [arg-map (apply decompose-defn-args* args)
        arg-map (-> arg-map
                    (update-in [:arities] (fn [ars]
                                            (for [ar ars]
                                              (do
                                                (update-in ar [:body]
                                                           (fn [body]
                                                             (let [params (-> ar :params)
                                                                   validate-exprs (for [p params]
                                                                                    (when-let [tag (-> p (meta) :tag)]
                                                                                      `(validate-symbol (quote ~p) ~p ~tag)))]
                                                               `(do ~@validate-exprs
                                                                    ~@body)))))))))]
    `(defn-map ~arg-map)))
