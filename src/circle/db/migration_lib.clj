(ns circle.db.migration-lib
  "Library for performing DB migrations in mongo"
  (:require [somnium.congomongo :as mongo])
  (:require [clj-time.core :as time])
  (:use [clojure.tools.logging :only (infof)])
  (:use [circle.util.except :only (throw-if)])
  (:use [arohner.utils :only (inspect)]))

;; See docs/migration-design.txt in the source tree
(defonce migrations (ref []))

(defn clear-migrations []
  (dosync
   (alter migrations (constantly []))))

(defn add-migration [m]
  (dosync
   (alter migrations conj m)
   ;; Check the number mathes the position. This could be implicit, but explicit
   ;; feels safer.
   (assert (= m (->> m :num (get @migrations))))))

;; the DB column that will be written to every row on every successful migration
(def mcol :migration_version)

(defn def-migration
  "Returns a new migration object. Query is the :where clause of a
  congomongo query. transform is a fn of one argument, the old
  row. transform should return the updated row."
  [name & {:keys [coll query transform] :as args}]
  (add-migration (merge args {:name name})))

(defn migration-in-progress? []
  (-> (mongo/fetch :migration_log :where {:start_time {:$exists true}
                                          :end_time {:$exists false}})
      (first)))

(defn run-migration [m]
  (infof "starting migration %s" (-> m :name))
  (let [in-progress (migration-in-progress?)]
    (throw-if in-progress "migration in progress: %s on %s" (-> in-progress :name) (-> in-progress :hostname)))

  (let [version (-> m :num)
        log (mongo/insert! :migration_log (merge (select-keys m [:name :num :coll]) {:start_time (-> (time/now) .toDate) :hostname (circle.env/hostname)}))
        rows-affected (atom 0)]
    (doseq [row (mongo/fetch (-> m :coll) :where (merge (if-let [q (-> m :query)]
                                                          q
                                                          {}) {mcol {:$not (-> m :num)}}))
            :let [coll (-> m :coll)
                  t (-> m :transform)
                  new (merge (t row) {mcol version})]]
      (mongo/update! coll row new)
      (swap! rows-affected inc))
    (mongo/update! :migration_log log (assoc log :end_time (-> (time/now) .toDate) :rows_affected @rows-affected)))
  (infof "finished migration %s" (-> m :name)))

(defn db-schema-version []
  (->
   (mongo/fetch :migration_log :sort {:num 1, :end_time 1})
   (last)
   :num
   (or -1)))

(defn necessary-migrations
  "Return the list of migrations that should be run, up to version N"
  [n]
  (->> (range (inc (db-schema-version)) (inc n))
       (map #(get @migrations %))))

(defn run-migrations
  "Run all migrations, from the DB current version, to n, inclusive"
  [n]
  (infof "starting migrations")
  (doseq [m (necessary-migrations n)]
    (run-migration m))
  (infof "migrations finished"))