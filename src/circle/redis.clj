(ns circle.redis
  (:require [circle.env])
  (:require [clj-url.core])
  (:use [circle.util.except :only (throw-if-not)])
  (:use [circle.util.core :only (defn-once)])
  (:require [redis.core :as redis]))

(def uris {:development {:host "barracuda.redistogo.com"
                         :port 9477
                         :username "circle"
                         :password "45a7c5aeab1ae5ecf79b95b898232d6c"}
           :test {:host "carp.redistogo.com"
                  :port 9334
                  :username "circle"
                  :password "6c67063efb4a63915cf499d4cbc7d12e"}})

;; (def ^:dynamic *current-account* nil)
(def ^:dynamic *current-db* nil)

;; connection fns. Used by redis (but not resque)

(defmacro with-redis [& body]
  `(redis/with-server *current-db*
     ~@body))

(defn-once init
  (alter-var-root (var *current-db*) (constantly (get uris (circle.env/env))))
  (redis/set-server! *current-db*))