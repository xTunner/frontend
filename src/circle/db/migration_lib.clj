(ns circle.db.migration-lib
  "Library for performing DB migrations in mongo"
  (:require [somnium.congomongo :as mongo])
  (:require [clj-time.core :as time])
  (:require [circle.backend.load-balancer :as lb])
  (:require [circle.api.client.system :as system])
  (:require [circle.backend.ec2 :as ec2])
  (:use [clojure.tools.logging :only (infof errorf)])
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
    (try
      (doseq [row (mongo/fetch (-> m :coll) :where (merge (if-let [q (-> m :query)]
                                                            q
                                                            {}) {mcol {:$ne (-> m :num)}}))
              :let [coll (-> m :coll)
                    t (-> m :transform)
                    new (merge (t row) {mcol version})]]
        (mongo/update! coll row new)
        (swap! rows-affected inc))
      (mongo/update! :migration_log log (assoc log :end_time (-> (time/now) .toDate) :rows_affected @rows-affected :result :success))
      (catch Exception e
        (errorf e "error while running migration %s" m)
        (mongo/update! :migration_log log (assoc log :end_time (-> (time/now) .toDate) :rows_affected @rows-affected :result :failed))
        (throw e))))
  (infof "finished migration %s" (-> m :name)))

(defn db-schema-version
  "Returns the schema version on the DB"
  []
  (->
   (mongo/fetch :migration_log :where {:result :success} :sort {:num 1, :end_time 1})
   (last)
   :num
   (or -1)))

(defn code-schema-version
  "Returns the latest migration the code knows about"
  []
  (-> @migrations (count) (dec)))

(defn necessary-migrations*
  "Return the list of migrations from the current version up to version N"
  [n]
  (->> (range (inc (db-schema-version)) (inc n))
       (map #(get @migrations %))))

(defn necessary-migrations-local
  "Returns the list of migrations that can be run locally"
  []
  (necessary-migrations* (code-schema-version)))

(defn necessary-migrations-production
  "Returns the list of migrations that can be run in production"
  []
  (let [production-servers (lb/instances "www")
        to-version (->> production-servers
                        (map ec2/public-ip)
                        (map system/code-schema-version)
                        (apply min))]
    (necessary-migrations* (code-schema-version))))

(defn run-migrations
  "Run all supplied migrations"
  [migrations]
  (infof "starting migrations")
  (doseq [m migrations]
    (run-migration m))
  (infof "migrations finished"))

(defn necessary-migrations []
  (if (circle.env/production?)
    (necessary-migrations-production)
    (necessary-migrations-local)))

(defn run-necessary-migrations []
  (run-migrations (necessary-migrations)))