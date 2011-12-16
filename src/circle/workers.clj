(ns circle.workers
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.project.circle :as circle])
  (:require [circle.backend.build.config :as config])
  (:use [clojure.tools.logging :only (infof errorf error)])
  (:require [circle.ruby :as ruby])
  (:use midje.sweet)
  (:require [clj-airbrake.core :as airbrake])
  (:require [circle.env :as env])
  (:require [fs]))

; TODO: find another home for this
(defn run-build-from-jruby
  [project-name job-name]
  (let [build (config/build-from-name project-name :job-name (keyword job-name))]
    (run/run-build build)))


;;; Workers. Handles starting workers, checking if they're done, and getting the
;;; result.
(defn call-clojure-from-ruby [f args]
  (ruby/->ruby (apply f args)))

(def worker-store (ref {}))

(defmacro log-future
  "Takes a function to be called in a future. Starts the future and logs the result at the end"
  [& body]
  `(future
     (try
       (let [result# (do ~@body)]
         (infof "%s returned %s" (quote  ~@body) result#)
         result#)
       (catch Exception e#
         (error e# "%s threw" (quote ~@body))
         (airbrake/notify "3ccfad1720b6acd8c82e9b30d4b95a30" env/env (fs/cwd) e# {:body (quote ~@body) :future true :url "http://fakeurl.com"})
         (throw e#)))))

(fact "log-future returns futures"
  ;; A normal future will return a future object here, even if the
  ;; code obviously throws an exception. Make sure that's the case
  ;; here as well.
  (log-future (/ 1 0)) => future?)

(fact "log-future works"
  @(log-future (apply + [1 1])) => 2)

(defn start-worker [f & args]
  "Call fn with args as a worker. Returns the id of the worker"
  (let [fut (log-future
             (call-clojure-from-ruby f args))]
    (dosync
     (let [next-id (count @worker-store)]
       (infof "starting worker id %s for %s" next-id f)
       (alter worker-store assoc next-id fut)
       next-id))))

(defn fire-worker [f & args]
  "Start a worker, but don't wait for a response"
  (log-future (call-clojure-from-ruby f args))
  nil)

(defn blocking-worker [f & args]
  "Start a worker and block until it returns"
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
         fut (get @worker-store as-int)
         ; TODO: when we dereference this, it may result in throwing the
         ; exception that occurred within the future. We want the stack trace
         ; that appears on error to be that stack-trace.
         result (deref fut)]

     (alter worker-store dissoc as-int)
     result)))

(defn worker-count []
  (dosync
   (count @worker-store)))