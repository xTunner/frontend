(ns circle.utils.validation
  (:use [circle.utils.except :only (throwf throw-if throw-if-not)])
  (:use [circle.utils.predicates :only (namespace?)])
  (:use [circle.utils.macro :only (decompose-defn-args* defn-map* defn-map)])
  (:use [circle.utils.core :only (seq1)])
  (:use [arohner.utils :only (inspect supports-meta?)]))

(defn validation-error
  "validates an object. validation-seq is a seq of vectors. Each vector contains a fn of one argument that will be applied with obj. If the fn returns non-truthy, format will be applied with the rest of the arguments in the vector.

 Returns the string for the first validation to fail, or nil

 In format expressions, :$ will be replaced with the input obj, fns will be called with one argument, the input obj
 example:
 (validate [[map? \"obj must be a map, got %s\" :$]
            [:type \"m must contain a field :type\"]
            [#(int? (-> % :foo)) \":foo must be an int, got %s\" #(-> % :foo class)] m) "
  [validation-seq obj]
  (->>
   (for [vseq (seq1 validation-seq)]
     (do
       (let [[v-fn & format-args] vseq
             resp (v-fn obj)]
         (when-not resp
           (apply format (map (fn [arg]
                                (cond
                                 (= arg :$) obj
                                 (fn? arg) (arg obj)
                                 :else arg))  format-args))))))
   (filter identity)
   (first)))

(defn valid?
  "Similar to validation-error, but returns a boolean"
  [validation-seq obj]
  (not (string? (validation-error validation-seq obj))))

(defn validate!
  "Similar to validation-error, but returns the obj being validated, or throws on failure"
  [validation-seq obj]
  (if-let [resp (validation-error validation-seq obj)]
    (throw (Exception. resp))
    obj))

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

(def node-validation [[map? "node must be a map"]
                      [:foo "node must contain a key :foo"]
                      [:bar "node must contain a key :bar"]
                      [#(integer? (:foo %)) "foo must be an int"]
                      [#(even? (:foo %)) "foo must be even, got %s" #(:foo %)]
                      [#(odd? (:bar %)) "bar must be odd"]])

(defmethod validate :user/Node [tag obj]
  (validate! node-validation obj))

(defn-v f [^:user/Node x] (println x))
