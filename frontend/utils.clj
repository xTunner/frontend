(ns frontend.utils)

(defmacro inspect
  "prints the expression '<name> is <value>', and returns the value"
  [value]
  `(do
     (let [name# (quote ~value)
           result# ~value]
       (print (pr-str name#) "is" (pr-str result#))
       result#)))
