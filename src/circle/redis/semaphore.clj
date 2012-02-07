(ns circle.redis.semaphore
  (:use [circle.util.except :only (throw-if-not)])
  (:require [redis.connection-timeout])
  (:use [arohner.utils :only (inspect)])
  (:use [redis.pipeline :only (pipeline)])
  (:require [redis.core :as redis]))

(defn used-list [name]
  (format "SEMAPHORE:%s:used" (clojure.core/name name)))

(defn free-list [name]
  (format "SEMAPHORE:%s:free" (clojure.core/name name)))

(defn create [name sem-count]
  (let [temp-name (str name (rand-int Integer/MAX_VALUE))]
    (let [resp (pipeline
                (redis/del temp-name)
                (doseq [i (range 0 sem-count)]
                  (redis/lpush temp-name (str name "-" i)))
                (redis/renamenx temp-name (free-list name))
                (redis/del temp-name))
          rename-resp (nth resp (- (count resp) 2))
          created? (= 1 rename-resp)]
      created?)))

(defn delete [name]
  (redis/del (used-list name) (free-list name)))

(defn free-count [name]
  (redis/llen (used-list name)))

(defn used-count [name]
  (redis/llen (free-list name)))

(defn sane-actual [name]
  (+ (free-count name)
     (used-count name)))

(defn sane? [name expected-count]
  (let [actual-count (sane-actual name)]
    (= expected-count actual-count)))

(defn sanity-check! [name expected-count]
  (throw-if-not (sane? name expected-count) "sanity check failed for %s. expected %s, got %s" name expected-count (sane-actual name)))

(defn lock
  "lock the semaphore. Blocking. Returns a token id. Must call release with token id. See also, with-lock "
  [name & {:keys [timeout]
           :or {timeout 1000}}]
  (redis.connection-timeout/with-timeout timeout
    (redis/brpoplpush (free-list name) (used-list name) timeout)))

(defn release [name token & [timeout]]
  (redis/multi)
  (redis/lrem (used-list name) 0 token)
  (redis/lpush (free-list name) token)
  (redis/exec))

(defmacro with-lock [name opts & body]
  `(let [name# ~name
         token# (lock name# :timeout (-> ~opts :timeout))]
     (try
       ~@body
       (finally
        (release name# token#)))))