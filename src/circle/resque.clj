(ns circle.resque
  (:require [circle.env])
  (:use [circle.util.core :only (defn-once)])
  (:require [resque-clojure.core :as resque])
  (:require [resque-clojure.redis :as redis]))

(def uris {:development {:host "barracuda.redistogo.com"
                         :port 9477
                         :username "circle"
                         :password "45a7c5aeab1ae5ecf79b95b898232d6c"}
           :test {:host "carp.redistogo.com"
                  :port 9334
                  :username "circle"
                  :password "6c67063efb4a63915cf499d4cbc7d12e"}
           :staging {:host "barracuda.redistogo.com"
                     :port 9553
                     :username "circle"
                     :password "9244c6279038eb6ead961a71320ea873"}
           :production {:host (System/getenv "REDIS_HOST")
                        :port (System/getenv "REDIS_PORT")
                        :username (System/getenv "REDIS_USERNAME")
                        :password (System/getenv "REDIS_PASSWORD")}})

(defn-once init
  (resque/configure (merge (get uris (circle.env/env))
                           {:max-workers 10}))
  (resque/start ["builds"]))

(defn print-queue [queue-name]
  (redis/lrange (resque-clojure.resque/-full-queue-name queue-name) 0 -1))

(defn print-workers []
  (resque-clojure.redis/smembers "resque:workers"))

(defmacro with-resque [conn-map & body]
  `(do
     (let [old-conn-map# @resque-clojure.redis/config]
       (try
         (redis/configure ~conn-map)
         (redis/init-pool)
         ~@body
         (finally
          (redis/configure old-conn-map#)
          (redis/init-pool))))))

(defmacro with-test-resque [& body]
  `(with-resque (-> uris :test)
     ~@body))
