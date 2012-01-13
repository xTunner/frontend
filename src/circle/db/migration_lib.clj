(ns circle.db.migration-lib
  "Library for performing DB migrations in mongo"
  (:require [somnium.congomongo :as mongo])
  (:use [clojure.tools.logging :only (infof)])
  (:use [arohner.utils :only (inspect)]))

(defonce migrations (ref []))

(defn clear-migrations []
  (dosync
   (alter migrations (constantly []))))

(defn add-migration [m]
  (dosync
   (alter migrations conj m)
   ;; Check the number mathes the position. This could be implicit, but explicit
   ;; feels safer.
   (assert (= m (->> m :num (get migrations))))))

(def mcol :migration_version)

(defn def-migration
  "Returns a new migration object. Query is the :where clause of a
  congomongo query. transform is a fn of one argument, the old
  row. transform should return the updated row."
  [name & {:keys [coll query transform] :as args}]
  (add-migration (merge args {:name name})))

(defn run-migration [m]
  (infof "starting migration %s" (-> m :name))
  (let [version (-> m :num)]
    (doseq [row (mongo/fetch (-> m :coll) :where {mcol (- version 1)})
            :let [coll (-> m :coll)
                  t (-> m :transform)
                  new (t row)
                  new (merge new {mcol version})]]
      (mongo/update! coll row new)))
  (infof "finished migration %s" (-> m :name)))

(defn run-all-migrations []
  (infof "starting migrations")
  (doseq [m @migrations]
    (run-migration m))
  (infof "migrations finished"))