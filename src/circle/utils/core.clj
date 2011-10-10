(ns circle.utils.core)

(defn printfln [& args]
  (apply printf args)
  (newline))

(defn apply-map
  "Takes a fn and any number of arguments. Applies the arguments like
  apply, except that the last argument is converted into keyword
  pairs, for functions that keyword arguments."
  [f & args*]
  (let [normal-args (butlast args*)
        m (last args*)]
    (apply f (concat normal-args (flatten (seq m))))))

