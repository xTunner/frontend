(ns circle.util.except)

(defn throwf [& args]
  (throw (Exception. (apply format args))))

(defmacro throw-if [test & format-args]
  `(when ~test
     (throwf ~@format-args)))

(defmacro throw-if-not [test & format-args]
  `(when (not ~test)
     (throwf ~@format-args)))

(defmacro maybe
  "Assuming that the body of code returns X, this macro returns X in
  the case of no error and nil otherwise."
  [& body]
  `(try
     (do ~@body)
     (catch Exception e#
       nil)))