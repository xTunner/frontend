(ns circle.util.args
  (:use [circle.util.except :only (throw-if-not)]))

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
