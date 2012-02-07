(ns circle.redis.test-semaphore
  (:use circle.redis.semaphore)
  (:require circle.redis)
  (:use [circle.util.core :only (re)])
  (:use [arohner.utils :only (inspect)])
  (:require [redis.core :as redis])
  (:use midje.sweet))

(defn gen-name [& {:keys [name]}]
  (str (or name "test-semaphore") (rand-int Integer/MAX_VALUE)))

(defn temp-semaphore [& {:keys [name count]}]
  (let [sema-name (gen-name)]
    (delete sema-name)
    (create sema-name (or 2 count))
    sema-name))

(fact "creating semaphores works"
  (circle.redis/with-redis
    (let [sema-name (gen-name)]
      (delete sema-name)
      (create sema-name 3) => true
      (redis/lrange (free-list sema-name) 0 -1) => (contains string? string? string?)
      (create sema-name 3) => false)))

(fact "locking works"
  (circle.redis/with-redis
    (let [sema-name (temp-semaphore :count 2)]
      (lock sema-name) => (re sema-name)
      (lock sema-name) => (re sema-name)
      (lock sema-name) => (throws java.net.SocketTimeoutException))))

(fact "repeated locking"
  (circle.redis/with-redis
    (let [sema-name (temp-semaphore :count 2)]
      (dotimes [i 10]
        (let [token (lock sema-name)]
          (release sema-name token) => anything)))))