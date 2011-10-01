(ns circleci.db.migrations
  (:use [circleci.db :only (with-conn)])
  (:use [circleci.db.migration-lib :only (defmigration run-required-migrations reset-migration-state)])
  (:require [clojure.java.jdbc :as jdbc])
  (use (clojure.contrib [string :only (as-str)])))

(def migration-info (circleci.db.migration-lib/migration-info (.getName *ns*)))

(defn init []
  (with-conn
    (run-required-migrations migration-info))
  (println "migrations/init done"))

(reset-migration-state (.getName *ns*))

(defmigration "schema version"
  (println "create-table: schema version")
  (jdbc/create-table :schema_version
                     [:name :varchar "UNIQUE NOT NULL"]
                     [:date_run :timestamp "NOT NULL"]))

(defmigration "add beta notify"
  (println "create-table: beta notify")
  (jdbc/create-table :beta_notify
                     [:email :text "PRIMARY KEY"]
                     [:environment :text]
                     [:features :text]))

(defmigration "add beta checkbox"
  (println "alter-table: beta checkbox")
  (jdbc/do-commands "ALTER TABLE beta_notify ADD contact boolean"))



