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

; Since we don't authenticate a user's info/email, we can't just say "it looks like you signed up before, here's your info", so we use their session. I'm not sure how to make the session info DB based, so I'll just use the session/email as the primary key.
(defmigration "add session key"
  (println "alter-table: add session-key")
  (jdbc/do-commands "ALTER TABLE beta_notify ADD session-key text"))


