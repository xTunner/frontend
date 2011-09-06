(ns circleci.db.migrations
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

(def migration-name-regex #"(\d{4})-(.*).(?:clj|sql)")

(def migration-path "./src/circleci/db/migrations/")

(defn get-migration-id 
  [filename]
  "returns the serial id part of the migration filename"
  (Integer/parseInt ((re-find migration-name-regex filename) 1)))

(defn get-migration-path
  [filename]
  "returns the path to file, given its filename."
  (str migration-path filename))

(defn get-migration-namespace
  [filename]
  "returns the namespace of the contents of the file"
  (or 
   (find-ns (symbol (str "analyzer.db.migrations." ((re-find migration-name-regex filename) 2))))
   (throw (new Exception "namespace not found"))))

(defn get-migration-list 
  "returns a list of file names of migrations in the migration-path"
  []
  (let [file-list (-> migration-path
                      (File.)
                      (.list)
                      seq)]
    (if (= 0 (count file-list))
      nil
      (sort (seq file-list)))))

(defn latest-version
  "returns the latest available version from migration-list"
  []
  (last (get-migration-list)))

(defn next-migration-id
  []
  (if (= nil (latest-version))
    1
    (inc (get-migration-id (latest-version)))))

(defn get-root-cause [exception]
  "keeps getting the cause of e until there are no more"
  (loop [e exception]
    (if (.getCause e)
      (recur (.getCause e))
      e)))

(defn psql-error-code [e]
  "returns the PSQL error code on this exception, if any, otherwise nil"
  (let [root-cause (get-root-cause e)]
    (when (= (class root-cause) org.postgresql.util.PSQLException)
      (.getSQLState (.getServerErrorMessage root-cause)))))

(defn no-db-exception? 
  "returns true if e is the exception that represents 'the DB doesnt exist'"
  [e]
  (= (psql-error-code e) "3D000"))

(defn no-table-exception?
  "returns true if e is the exception that represents 'the table doesnt exist'"
  [e]
  (= (psql-error-code e) "42P01"))

(defn get-db-current-version
  "returns the current DB version, or nil if there isn't a version yet"
  []
  (try
   (let [ver-str (:version (first (table-sql/query "select * from schema_version")))]
     (if ver-str
       (Integer/parseInt ver-str)
       nil))
   (catch Exception e
     (if (no-table-exception? e)
       nil
       (throw e)))))

(defn set-db-version 
  "sets the DB version"
  [ver]
  (if (get-db-current-version)
    (jdbc/do-prepared "update schema_version set version=?" [ver])
    (jdbc/insert-record "schema_version" {:version ver}))
  (.warn (Logger/getLogger "migrations") (str "set DB version to " ver)))
  
(defn required-migrations 
  "gets the list of migrations that need to be performed"
  []
  (let [current (get-db-current-version)
        migration-list (get-migration-list)]
    (if current
      (seq (drop-while #(<= (get-migration-id %) current) migration-list))
      migration-list)))

(defn get-migration-type 
  [filename]
  (cond 
    (re-find #".*.clj$" filename) :clj
    (re-find #".*.sql$" filename) :sql))

(defn get-connection-db [connection]
  (-> connection
      .getMetaData
      .getURL
      (str/split #":")
      (nth 2)))

(defmulti migrate-specific get-migration-type :default nil)

(defn sh!
  "like sh, but throws on failure"
  [& args]
  (let [result (apply sh (concat args [:return-map true]))]
    (if (not (zero? (-> result :exit)))
      (throw (Exception. (format "Command returned %d: %s" (-> result :exit) (-> result :err))))
      result)))

(defmethod migrate-specific :sql
  ([filename]
     (let [db-name (if-let [conn (jdbc/find-connection)]
                     (get-connection-db conn)
                     (-> db/db-map :db))]
       (assert db-name)
       (println "migrating DB" db-name)
       (sh! "psql" "-U" "postgres" "-d" db-name "-v" "ON_ERROR_STOP" "-c" (slurp (io/reader (get-migration-path filename)))))))

(defmethod migrate-specific :clj [filename]
  (load-file (get-migration-path filename)))

(defn migrate [filename]
  (.warn (Logger/getLogger "migrations") (str "running migration " filename))
  ;; TODO the migration and set-db-version should be in the same transaction
  (migrate-specific filename)
  (set-db-version (get-migration-id filename)))

(defn run-required-migrations []
  (doseq [migration (required-migrations)]
    (migrate migration)))

(defn rebuild-database []
  (jdbc/do-prepared "update schema_version set version = null")
  (run-required-migrations))
    
(defn create-migration [name]
  "writes a migration stub in the migration dir"
  (let [version-str (next-migration-id)
        filename (format "%04d-%s.sql" version-str name)
        file (File. (str migration-path filename))
        migration "begin transaction;\ncommit;\n"]
    (spit (io/writer file) migration)
    (println "created new migration" file)
    filename))

(defn init []
  (db/with-conn
    (run-required-migrations))
  (println "migrations/init done"))