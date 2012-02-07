(ns circle.redis
  (:require [circle.env])
  (:require [clj-url.core])
  (:use [circle.util.except :only (throw-if-not)])
  (:use [circle.util.core :only (defn-once)])
  (:require [redis.core :as redis]))

(def uris {:development "redis://circle:45a7c5aeab1ae5ecf79b95b898232d6c@barracuda.redistogo.com:9477"
           :test "redis://circle:6c67063efb4a63915cf499d4cbc7d12e@carp.redistogo.com:9334/"})

(def accounts (into {} (for [[env uri] uris]
                         [env (-> uri
                                  (clj-url.core/parse)
                                  (update-in [:port] #(Integer/parseInt %)))])))

(def ^:dynamic *current-account* nil)
(def ^:dynamic *current-db* nil)

;; connection fns. Used by redis (but not resque)

(defn set-connection! [db]
  (redis.connection/with-connection connection redis.vars/*pool* db
    (let [channel (redis.channel/make-direct-channel connection)]
      (alter-var-root (var redis.vars/*channel*) (constantly channel)))))

(defmacro with-redis [& body]
  `(redis.core/with-server *current-db*
     ~@body))

(defn init []
  (alter-var-root (var *current-account*) (constantly (get uris (circle.env/env))))
  (alter-var-root (var *current-db*) (constantly (get accounts (circle.env/env))))
  (set-connection! *current-db*))



