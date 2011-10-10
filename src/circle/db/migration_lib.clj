(ns circle.db.migration-lib
  (:import java.io.File)
  (:import (org.apache.log4j Logger))
  (:import org.apache.commons.io.FilenameUtils)
  (:import java.net.URL)
  (:require [clojure.java.jdbc :as jdbc])
  (:require [clj-table.sql :as table-sql])
  (:require [clojure.java.io :as io])
  (:require [circle.db :as db])
  (:require [clojure.string :as str])
  (:use [clojure.contrib.shell :only (sh)]))

(defn get-root-cause [exception]
  "keeps getting the cause of e until there are no more"
  (loop [e exception]
    (if (.getCause e)
      (recur (.getCause e))
      e)))

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
  (jdbc/transaction
   (jdbc/delete-rows (:table-name migration-info) [true])
   (jdbc/insert-record (:table-name migration-info) {:version version})))

(def migration-order (ref 0))

(defn mark-migration-run [migration-info migration-var]
  ;; FIXME: table name, migration var vs. migration-info
  (jdbc/insert-record (:table-name migration-info) {:name (-> migration-var meta ::migration-name)
                                                    :date_run (java.sql.Timestamp. (System/currentTimeMillis))}))

(defmacro defmigration
  "defines a DB migration. "
  [name & body]
  (dosync
   (alter migration-order inc)
   (let [migration-num @migration-order
         migration-var (gensym "migration")]
     `(defn ~migration-var {::migration true
                        ::migration-name ~name
                        ::migration-order ~migration-num} [migration-info#]
                        (db/with-conn
                          (try
                            (jdbc/transaction
                             ~@body
                             (mark-migration-run migration-info# (var ~migration-var)))
                            (catch Exception e#
                              (when (= (class e#) java.sql.BatchUpdateException) ;; (contains? (ancestors e#) java.sql.BatchUpdateException)
                                (print (.getNextException e#)))
                              (throw e#))))))))

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

(defn reset-migration-state
  "If developing in emacs or some other IDE that reload code, reset
  all migration related state so you don't accidentally create extra
  migrations when reloading your migration file."
  [ns]
  (->>
   (ns-publics ns)
   (vals)
   (filter #(-> % meta ::migration))
   (map #(-> % meta :name))
   (map #(do (println "unmapping" %) (ns-unmap ns %)))
   (doall))
  (dosync (alter migration-order (constantly 0))))

(defn find-migrations [migration-info]
  (let [ns (:migration-ns migration-info)
        run-migrations (get-run-migrations migration-info)]
    (->> ns
         (ns-publics)
         (vals)
         (filter #(-> % meta ::migration))
         (remove #(contains? run-migrations (-> % meta ::migration-name)))
         (sort-by #(-> % meta ::migration-order)))))

(defn run-required-migrations [migration-info]
  (->> migration-info
       (find-migrations)
       (map #(do
               (println "running" (-> % meta ::migration-name) (-> % meta ::migration-order))
               (% migration-info)))
       (doall)))