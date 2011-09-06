(ns circleci.db.migration-lib
  (:import java.io.File)
  (:import (org.apache.log4j Logger))
  (:import org.apache.commons.io.FilenameUtils)
  (:import java.net.URL)
  (:require [clojure.java.jdbc :as jdbc])
  (:require [clj-table.sql :as table-sql])
  (:require [clojure.java.io :as io])
  (:require [circleci.db :as db])
  (:require [clojure.string :as str])
  (:use [clojure.contrib.shell :only (sh)]))

(defn migration-info
  "returns a map containing all the info needed to support migrations. Other migration fns will take this as an argument. Arguments:
   ns - a symbol, the namespace containing the defmigration calls.
   table-name - (optional) the name of the table where migration info will be stored. Defaults to 'schema_version'
   column-type - (optional) the type of the column used to store the name of the last migration. Defaults to 'column-type'"
  [ns & {:keys [table-name column-type]}]
  {:migration-ns ns
   :table-name (or table-name "schema_version")
   :column-type (or column-type "varchar")})

(defn create-schema-table
  "creates the schema table. Optional keys: :table-name, :column-type. If not specified, defaults to 'schema_version' and 'varchar'"
  [migration-info]
  (jdbc/create-table (:table-name migration-info)
                     [:id :primary-key]
                     [:version (:column-type migration-info) "NOT NULL"]
                     [:tstamp :timestamp "default now()"]))

(defn get-schema-version [migration-info]
  (jdbc/with-query-results results [(format "select * from %s" (:table-name migration-info))]
    (-> results first :version)))

(defn set-schema-version [migration-info version]
  (println "set-schema-version: " version)
  (jdbc/transaction
   (jdbc/delete-rows (:table-name migration-info) [true])
   (jdbc/insert-record (:table-name migration-info) {:version version})))

(def migration-order (ref 0))

(defmacro defmigration
  "defines a DB migration. "
  [name & body]
  (dosync
   (alter migration-order inc)
   (let [migration-num @migration-order]
     `(defn ~(gensym "migration") {::migration true
                        ::migration-name ~name
                        ::migration-order ~migration-num} [migration-info#]
                        (db/with-conn
                          (jdbc/transaction
                           ~@body
                           (set-schema-version migration-info# ~migration-num)))))))

(defn get-run-migrations
  "returns a seq of migration names that have already been run"
  [migration-info]
  (try
    (jdbc/with-query-results results [(format "select name from %s" (:table-name migration-info))]
      (->> results
           (map :name)
           (into #{})
           (doall)))
    (catch Exception e
      nil)))

(defn unmap-migrations [ns]
  (->>
   (ns-publics ns)
   (vals)
   (filter #(-> % meta ::migration))
   (map #(-> % meta :name))
   (map (fn [m]
          (println "unmapping" m "in " ns)
          (ns-unmap ns m)))
   (doall)))

(defn find-migrations [migration-info]
  (let [ns (:migration-ns migration-info)
        run-migrations (get-run-migrations migration-info)]
    (println "migration-info: migration-info:" migration-info)
    (println "find-migrations: ns=" ns)
    (unmap-migrations ns)
    (dosync (alter migration-order (constantly 0)))
    (require ns :reload)
    (->> ns
         (ns-publics)
         (vals)
         (filter #(-> % meta ::migration))
         ((fn [i]
           (do (println "pre-remove:" i) i)))
         (remove #(contains? run-migrations (-> % meta ::migration-name)))
         ((fn [i]
           (do (println "post-remove:" i) i)))
         (sort-by #(-> % meta ::migration-order)))))

(defn run-required-migrations [migration-info]
  (->> migration-info
       (find-migrations)
       (map #(do
               (println "running" (-> % meta ::migration-name) (-> % meta ::migration-order))
               (% migration-info)))
       (doall)))

