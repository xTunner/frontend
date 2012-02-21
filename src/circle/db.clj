(ns circle.db
  (:require [circle.env :as env])
  (:require [somnium.congomongo :as mongo])
  (:require [monger.core :as monger])
  (:use [circle.util.core :only (defn-once)])
  (:use [circle.util.except :only (throwf)])
  (:require [circle.db.migration-lib])
  (:require [circle.db.migrations])
  (:use [clojure.tools.logging :only (infof)]))

(def production-db ; mongodb://<user>:<password>@staff.mongohq.com:10078/large1
  {:db (System/getenv "MONGOHQ_DATABASE")
   :host (System/getenv "MONGOHQ_HOST")
   :port (Integer/parseInt (or (System/getenv "MONGOHQ_PORT") "0"))
   :username (System/getenv "MONGOHQ_USERNAME")
   :password (System/getenv "MONGOHQ_PASSWORD")})

(def staging-db ; mongodb://<user>:<password>@staff.mongohq.com:10017/staging
  {:db :staging
   :host "staff.mongohq.com"
   :port 10017
   :username "circle"
   :password "construction-ship-plates-proud"})

(def test-db
  {:db :mongoid_test_test
   :host "127.0.0.1" ; not "localhost". No idea why, but using "localhost"
                     ; doesn't work on paul's laptop
   :port 27017})

(def development-db
  {:db :circle
   :host "127.0.0.1" ; not "localhost", see above.
   :port 27017})

(defn default-db []
  (cond
   (env/production?) production-db
   (env/staging?) staging-db
   (env/test?) test-db
   (env/development?) development-db
   :else (throwf "no environment set")))

(defn congo-connect [db]
  (let [{:keys [db host port username password]} db
        conn (mongo/make-connection db :host host :port port)]
    (mongo/with-mongo conn
      (when (and username password)
        (mongo/authenticate username password))
      (mongo/set-write-concern conn :strict))
    conn))

(defn start-mongo
  "Initializes the mongodb connection"
  [& [db]]
  (let [db (or db (default-db))]
    (infof "Connecting with congomongo: %s" (dissoc db :password))
    (def congodb (congo-connect db))
    (mongo/set-connection! congodb)))

(defn monger-connect [db]
  (let [{:keys [db host port username password]} db
        db-name (name db)
        conn (monger/connect { :host host :port port})
        db-itself (monger.core/get-db conn db-name)]
    (monger.core/set-db! db-itself)
    (monger.core/with-db db-itself
      (when (and username password)
        (monger.core/authenticate db-name username (.toCharArray password))))
    ; WriteConcern is SAFE by default
    db-itself))

(defn start-monger [& [db]]
  "Initialize monger's mongodb connection"
  (let [db (or db (default-db))]
    (infof "Connecting with monger: %s" (dissoc db :password))
    (def mongerdb (monger-connect db))
    (monger.core/connect! db)))

(defn indices []
  (mongo/add-index! :users [:email] :unique true)
  (mongo/add-index! :projects [:vcs_url] :unique true))

(defn init []
  (when (not (bound? (var congodb)))
    (start-mongo)
    (indices)
    (start-monger)
    (println "db/init done")))

(defmacro with-production-db
  "Execute some code while connected to the production DB"
  [& body]
  `(mongo/with-mongo (congo-connect production-db)
     (monger.core/with-db (monger-connect production-db)
       ~@body)))

(defn-once test-congo-connection
  (congo-connect test-db))

(defn-once test-monger-connection
  (monger-connect test-db))