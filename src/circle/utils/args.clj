(ns circle.utils.args
  (:use [circle.utils.except :only (throw-if-not)])
  (:use [midje.sweet]))

(defmacro require-arg
  "Throws if the argument is nil"
  [arg]
  `(throw-if-not ~arg (str (quote ~arg) " is required")))

(defmacro require-args
  "Throws if any of the arguments are nil"
  [& args]
  (let [forms (for [arg args]
                `(require-arg ~arg))]
    `(do
       ~@forms)))

(defn f [foo]
  (require-arg foo)
  foo)

(defn g [foo bar]
  (require-args foo bar)
  (+ foo bar))

(fact "require-arg works"
  (f 2) => 2
  (f nil) => (throws Exception "foo is required"))

(fact "require-args works"
  (g 1 2) => 3
  (g 1 nil) => (throws Exception "bar is required")
  (g nil 2) => (throws Exception "foo is required")
  (g nil nil) => (throws Exception))

