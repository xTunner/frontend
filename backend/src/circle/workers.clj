(ns circle.workers
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.project.circle :as circle])
  (:require [circle.backend.build.config :as config]))

; TODO: find another home for this
(defn run-build-from-jruby
  [project-name job-name]
  (let [build (config/build-from-name project-name :job-name (keyword job-name))]
    (run/run-build build)))


;;; Workers. Handles starting workers, checking if they're done, and getting the result.
(def worker-store (ref {}))

(defn start-worker [fn & args]
  "Call fn with args as a worker. Returns the id of the worker"
  (dosync
   (let [the-ref (future (apply fn args))
         next-id (count @worker-store)]
     (alter worker-store assoc next-id the-ref)
     next-id)))

(defn fire-worker [fn & args]
  "Start a worker, but don't wait for a response"
  (future (apply fn args))
  nil)

(defn worker-done? [id]
  "Return if the worker is done. Throw an NPE if there is no such worker"
  (dosync
   (let [as-int (int id)
         the-ref (get @worker-store as-int)]
     (future-done? the-ref))))

(defn wait-for-worker [id]
  "Block until the worker is done, and return it's result. Will only work once, next time it throws a NPE because the worker is no longer available"
  (dosync
   (let [as-int (int id)
         the-ref (get @worker-store as-int)
         result (deref the-ref)]
     (alter worker-store dissoc as-int)
     result)))

(defn worker-count []
  (dosync
   (count @worker-store)))