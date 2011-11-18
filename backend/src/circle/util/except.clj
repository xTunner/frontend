(ns circle.util.except)

(defn throwf [& args]
  (throw (Exception. (apply format args))))

(defmacro throw-if [test & format-args]
  `(when ~test
     (throwf ~@format-args)))

(defmacro throw-if-not [test & format-args]
  `(when (not ~test)
     (throwf ~@format-args)))

(defmacro eat
  "Executes body, catching all exceptions. Returns the result of body, or nil if exceptions where caught"
  [& body]
  `(try
     (do ~@body)
     (catch Exception e#
       nil)))

(defmacro assert!
  "Asserts expr is truthy. Returns expr on success, or throws msg"
  [expr & msg]
  `(let [r# ~expr]
     (throw-if-not r# ~@(or msg ["%s returned %s" ~expr expr]))
     r#))