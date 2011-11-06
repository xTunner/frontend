(ns circle.util.model-validation-helpers)

(defmacro require-predicate [f & msg]
  `(fn [o#]
    (when-not (~f o#)
      (or ~msg (format "Object must pass predicate %s" (quote ~f))))))

(defmacro col-predicate
  "Require that f, a predicate, returns truthy when passed (get obj col)"
  [col f & [msg]]
  `(fn [o#]
     (when-not (~f (get o# ~col))
       (or ~msg (format "field %s must pass predicate %s" (quote ~col) (quote ~f))))))

(defn require-key* [o k]
  ;;{:post [(do (println "require-key* post:" k "=" %) true)]}                     
  (when-not (contains? o k)
    (format "column %s is required" k)))

(defn require-key [k]
  (fn [o]
    (require-key* o k)))

(defn map-predicate
  "Calls a validation fn on each item in coll, returning the first
  call to return an error string"
  [f s]
  (->> s
       (map f)
       (filter identity)
       (first)))

(defn require-keys
  "Validates that the map contains all of the keys"
  [keys]
  (fn [obj]
    (map-predicate #(require-key* obj %) keys)))

(defn key-type
  "Validates that the column is of the specified class"
  [k cls]
  (fn [o]
    (require-predicate #(instance? cls (get o k)) (format "key %s must be a %s" k cls))))

(defn key-types
  "Takes a map of keys to classes. Validates that each key in map is of the specified class."
  [ks]
  (fn [o]
    (map-predicate (fn [field cls]
                     (key-type o field cls)) ks)))