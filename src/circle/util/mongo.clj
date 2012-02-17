(ns circle.util.mongo
  "Fns for working with mongo"
  (:require [somnium.congomongo :as mongo])
  (:use [circle.util.except :only (throw-if-not)])
  (:import org.bson.types.ObjectId))

(defn object-id
  "Generate a new object id, return it."
  [& [id]]
  (if id
    (do
      (throw-if-not (string? id) "id must be a string")
      (ObjectId. id))
    (ObjectId.)))

(defn object-id?
  "True if o is an object-id"
  [o]
  (instance? org.bson.types.ObjectId o))

(defn coerce-object-id
  "Casts id from a string to an ObjectId if necessary"
  [id]
  (if (object-id? id)
    id
    (object-id id)))

(defn has-object-id?
  "True if map m has a mongo id"
  [m]
  (boolean (-> m :_id)))

(defn ensure-object-id
  "Adds a mongo id to m, a clojure map, if it doesn't have one."
  [coll m]
  {:post [(identity %)]}
  (if (not (has-object-id? m))
    (assoc m :_id (object-id))
    m))

(defn ensure-object-id-ref
  "Same as ensure-object-id, but works on refs of maps."
  [coll m]
  (dosync
   (when (not (has-object-id? @m))
     (alter m (constantly (ensure-object-id coll @m))))))