(ns circle.util.macro
  (:require [clojure.string :as str])
;  (:use midje.sweet)
  )

(defn decompose-defn-args* [& args]
  (letfn [(parse-name [args]
                      (assert (symbol? (first args)))
                      [(first args) (rest args)])
          (parse-doc-string [args]
                            (if (string? (first args))
                              [(first args) (rest args)]
                              [nil args]))
          (parse-attr-map [args]
                          (if (map? (first args))
                            [(first args) (rest args)]
                            [nil args]))
          (parse-single-arity [args]
            (assert (vector? (first args)))
            {:params (first args)
             :body (rest args)})
          (parse-multi-arity [args]
            (assert (seq args))
            (into [] (map parse-single-arity args)))
          (parse-arities [args]
            (if (vector? (first args))
              [(parse-single-arity args)]
              (parse-multi-arity args)))]
    (let [[name args] (parse-name args)
          [doc-string args] (parse-doc-string args)
          [attr-map args] (parse-attr-map args)
          arities (parse-arities args)]
      {:name name
       :doc-string doc-string
       :attr-map (merge attr-map (meta name))
       :arities arities})))

(defmacro  decompose-defn-args
  "interprets args the way defn would, returns a map that can be consumed by defn-map"
  [& args]
  `(apply decompose-defn-args* (quote ~args)))

;; (fact
;;   "decompose works"
;;   (let [resp (decompose-defn-args foo [x] (+ x x))]
;;     (:name resp) => 'foo
;;     (-> resp :arities count) => 1
;;     (-> resp :arities (first) :params) => '[x]
;;     (-> resp :arities (first) :body) => '((+ x x))))

;; (fact "decompose handles multiple arities"
;;   (let [resp (decompose-defn-args bar ([x] (+ x x)) ([x y] (+ x y)))]
;;     (:name resp) => 'bar
;;     (-> resp :arities count) => 2
;;     (-> resp :arities (second) :params) => '[x y]
;;     (-> resp :arities (second) :body) => '((+ x y))))

(defn defn-map* [arg-map]
  (let [defn-args (filter identity ((juxt :name :doc :attr-map) arg-map))
        arities (map (fn [ar]
                       (list (:params ar) (:body ar))) (:arities arg-map))]
    `(~@defn-args ~@arities)))

(defmacro defn-map
  "generates a defn expression, but arguments are a map, to make it
  easier on macro writers. Valid keys: name, doc-string, attr-map, arities. Arities is a seq, ([params] body)+
  expression, i.e. ([params] body)+ "
  [arg-map]
  `(defn ~@(defn-map* arg-map)))

(defn-map {:name defn-map-test-fn :doc "I haz a docstring" :arities [{:params [x]
                                                                       :body (* x x)} 
                                                                      {:params [x y]
                                                                       :body (+ x y)}]})

;; (fact "defn-map works"
;;   (defn-map-test-fn 5) => 25
;;   (defn-map-test-fn 5 3) => 8
;;   (-> (var defn-map-test-fn) (meta) :doc) => "I haz a docstring")



