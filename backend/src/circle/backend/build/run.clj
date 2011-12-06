(ns circle.backend.build.run
  (:require [circle.backend.build.email :as email])
  (:require [clj-time.core :as time])
  (:require [circle.backend.build :as build])
  (:use [arohner.utils :only (inspect fold)])
  (:require [circle.env :as env])
  (:use [circle.backend.action :as action])
  (:use [circle.backend.action.nodes :only (cleanup-nodes)])
  (:use [circle.logging :only (add-file-appender)])
  (:use [circle.util.except :only (throw-if throw-if-not)])
  (:use [clojure.tools.logging :only (with-logs error infof errorf)]))

;; Clojure has queues, they're not very well documented. They work
;; like other standard clojure structures, and support peek, pop,
;; conj.

(def queue (ref clojure.lang.PersistentQueue/EMPTY))

(def in-progress (ref #{}))
(def max-in-progress 5)
(def run-agent (agent nil))

(declare run-first)

(defn start* [build]
  (dosync
   (alter build assoc :start-time (-> (time/now) .toDate))))

(defn stop* [build]
  (dosync
   (alter build assoc :stop-time (-> (time/now) .toDate))))

(defn do-build* [build]
  (throw-if (-> @build :start-time) "refusing to run started build")
  (start* build)
  (doseq [act (-> @build :actions)]
    (when (-> @build :continue?)
      (let [current-act-results-count (count (-> @build :action-results))]
        (build/build-log "running %s" (-> act :name))
        (action/run-action build act))))
  (stop* build)
  build)

(defn log-result [b]
  (if (build/successful? b)
    (do
      (println "Build successful" (build/build-name b))
      (infof "Build %s successful" (build/build-name b)))
    (errorf "Build %s failed" (build/build-name b))))

(defn finished [b]
  (dosync
   (infof "removing build %s from in-progress" (build/build-name b))
   (alter in-progress disj b))
  (build/update-mongo b)
  (run-first))

(defn run-build [b & {:keys [cleanup-on-failure]
                          :or {cleanup-on-failure true}}]
  (def last-build b)
  (infof "starting build: %s" (build/build-name b))
  (try
    (build/with-build-log b
      (build/update-mongo b)
      (do-build* b)
      (email/notify-build-results b))
    b
    (catch Exception e
      (println "run-build: except: " b)
      (error e (format "caught exception on %s %s" (-> @b :project-name) (-> @b :build-num)))
      (println "assoc'ing failed?=true")
      (dosync
       (alter b assoc :failed? true))
      (when env/production?
        (email/send-build-error-email b e))
      (throw e))
    (finally
     (log-result b)
     (finished b)
     (when (and (-> @b :failed?) cleanup-on-failure) 
       (cleanup-nodes b)))))

(defn may-start-build? []
  (< (count @in-progress) max-in-progress))

(defn run-first []
  (dosync
   (when (may-start-build?)
     (when-let [b (peek @queue)]
       (alter queue pop)
       (alter in-progress conj b)
       (send-off run-agent run-build b)
       (infof "dequeing build %s" (build/build-name b))))))

(defn add-build [b]
  (dosync
   (alter queue conj b)
   (infof "adding build %s to the queue. There are %s queued builds" (build/build-name b) (count @queue)))
  (run-first))