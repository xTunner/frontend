(ns circleci.utils.except)

(defn throwf [& args]
  (throw (Exception. (apply format args))))

(defmacro throw-if [test & format-args]
  `(when ~test
     (throwf ~@format-args)))

(defmacro throw-if-not [test & format-args]
  `(when (not ~test)
     (throwf ~@format-args)))