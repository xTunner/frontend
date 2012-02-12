(ns circle.workers
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.project.circle :as circle])
  (:require [circle.backend.build.config :as config])
  (:use [clojure.tools.logging :only (infof errorf error)])
  (:require [circle.ruby :as ruby])
  (:use [circle.airbrake :only (airbrake)])
  (:require [circle.env :as env])
  (:require [fs])
  (:require [circle.model.project :as project]))

;;; Workers.
;;; Handles starting workers, checking if they're done, and getting the results.
(defn call-clojure-from-ruby [f args]
  (ruby/->ruby (apply f args)))

(def worker-store (ref {}))

(defmacro log-future
  "Takes a function to be called in a future. Starts the future and logs the result at the end"
  [& body]
  `(future
     (try
       (let [result# (do ~@body)]
         (infof "%s returned %s" (quote ~@body) result#)
         result#)
       (catch Exception e#
         (airbrake :data {:body (quote ~@body) :future true} :exception e#)
         (errorf e# "%s threw" (quote ~@body))
         (throw e#)))))

(defn start-worker [f & args]
  "Call fn with args as a worker. Returns the id of the worker"
  (let [fut (log-future
             (call-clojure-from-ruby f args))]
    (dosync
     (let [next-id (count @worker-store)]
       (infof "starting worker id %s for (%s %s)" next-id f args)
       (alter worker-store assoc next-id fut)
       next-id))))

(defn fire-worker [f & args]
  "Start a worker, but don't wait for a response"
  (infof "firing worker: (%s %s)" f args)
  (if env/test?
    (do
      (apply start-worker f args)  ; when testing, allow waiting for these to finish
      nil)
    (log-future (apply f args)))
  nil)

(defn blocking-worker [f & args]
  "Start a worker and block until it returns"
  (infof "blocking worker: (%s %s)" f args)
  (call-clojure-from-ruby f args))

(defn worker-done? [id]
  "Return if the worker is done. Throw an NPE if there is no such worker"
  (dosync
   (let [as-int (int id)
         fut (get @worker-store as-int)]
     (future-done? fut))))

(defn wait-for-worker [id]
  "Block until the worker is done, and return it's result. Will only work once, next time it throws a NPE because the worker is no longer available"
  (dosync
   (let [as-int (int id)
         fut (get @worker-store as-int)]
     (alter worker-store dissoc as-int)
     ;; The actual clojure exception is wrapped in a ExecutionException.
     (try
       (let [result (deref fut)]
         result)
       (catch java.util.concurrent.ExecutionException e
         (throw (.getCause e)))))))

(defn worker-count []
  (dosync
   (count @worker-store)))

(defn wait-for-all-workers []
  "Block until all workers are finished. Returns a list of the return values"
  (dosync
   (let [ks (keys @worker-store)]
     (infof "waiting for %s" ks)
     (doall (map wait-for-worker ks)))))