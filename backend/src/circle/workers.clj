(ns circle.workers
  (:require [org.danlarkin.json :as json])
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.project.circle :as circle])
  (:require [circle.backend.build.config :as config])
  (:use [circle.backend.github-url :only (->ssh)])
  (:use [clojure.tools.logging :only (infof)]))

(defn process-json [github-json]
  (when (= "CircleCI" (-> github-json :repository :name))
    (let [build (circle/circle-build)]
      (dosync
       (alter build merge
              {:vcs-url (->ssh (-> github-json :repository :url))
               :repository (-> github-json :repository)
               :commits (-> github-json :commits)
               :vcs-revision (-> github-json :commits last :id)
               :num-nodes 1}))
      (infof "process-json: build: %s" @build)
      (run/run-build build))))

(defn github
  [url after ref json-string]
  (-> json-string json/decode process-json))


(defn run-build-from-jruby
  [project-name job-name]
  (let [build (config/build-from-name project-name :job-name (keyword job-name))]
    (run/run-build build)))



;;; Workers. Handles starting workers, checking if they're done, and getting the result.
; TODO: there are almost certainly threading errors here, such as worker-store being a thread-local
; value.
(def worker-store (ref {}))

(defn run-worker [fn args]
  "Call fn with args as a worker. Returns the id of the worker"
  (dosync
   (let [the-ref (future (apply fn args))
         next-id (count @worker-store)]
     (alter worker-store assoc next-id the-ref)
     next-id)))

(defn check-worker [id]
  (dosync
   (let [the-ref (get @worker-store id)]
     (future-done? the-ref))))

(defn resolve-worker [id]
  (dosync
   (let [the-ref (get @worker-store id)
         _ (assert (future-done? the-ref))
         result (deref the-ref)]
     (alter worker-store dissoc id)
     result)))



