(ns frontend.utils)

(defmacro inspect
  "prints the expression '<name> is <value>', and returns the value"
  [value]
  `(do
     (let [name# (quote ~value)
           result# ~value]
       (print (pr-str name#) "is" (pr-str result#))
       result#)))

(defmacro timing
  "Evaluates expr and prints the label and the time it took.
  Returns the value of expr."
  {:added "1.0"}
  [label expr]
  `(let [start# (.getTime (js/Date.))
         ret# ~expr]
     (prn (str ~label " elapsed time: " (- (.getTime (js/Date.)) start#) " msecs"))
     ret#))
