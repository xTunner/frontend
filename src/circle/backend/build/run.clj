(ns circle.backend.build.run
  (:require [circle.backend.build.notify :as notify])
  (:require [circle.model.build :as build])
  (:use [arohner.utils :only (inspect fold)])
  (:require [circle.env :as env])
  (:require [clj-time.core :as time])
  (:use circle.util.straight-jacket)
  (:use [circle.backend.action :as action])
  (:use [circle.backend.action.nodes :only (cleanup-nodes)])
  (:require [circle.model.project :as project])
  (:use [circle.logging :only (add-file-appender)])
  (:use [circle.util.except :only (throw-if throw-if-not)])
  (:use [clojure.tools.logging :only (with-logs error infof errorf)]))

(def in-progress (ref #{}))

(defn do-build* [b]
  (doseq [act (-> @b :actions)]
    (when (-> @b :continue?)
      (let [current-act-results-count (count (-> @b :action-results))]
        (build/build-log "running %s" (-> act :name))
        (action/run-action b act)
        (build/update-mongo b))))
  b)

(defn log-result [b]
  (if (build/successful? b)
    (infof "Build %s successful" (build/build-name b))
    (errorf "Build %s failed" (build/build-name b))))

(defn start [b id]
  (dosync
   (build/add-to-db b id)
   (alter in-progress conj b)))

(defn finished [b]
  (dosync
   (infof "removing build %s from in-progress" (build/build-name b))
   (alter in-progress disj b)
   (alter b assoc :stop_time (-> (time/now) .toDate)))
  (let [project (build/get-project b)]
    (when (and (-> @b :actions (count) (zero?))
               (-> project :inferred)) ;; TODO
      (project/set-uninferrable project)))
  (build/update-mongo b))

(defn should-run-build-message
  "Returns a user-visible string describing why the build isn't being run, or nil when there are no errors"
  [b]
  (let [project (build/get-project b)]
    (cond
     (not (project/enabled? project)) (format "Project %s is not enabled, skipping" (-> project :vcs_url))
     :else nil)))

(defn should-run-build? [b]
  (if-let [msg (should-run-build-message b)]
    (dosync
     (alter b assoc :error_message msg)
     false)
    true))

(defn run-build [b & {:keys [cleanup-on-failure id]
                          :or {cleanup-on-failure true}}]
  (infof "starting build: %s, %s" (build/build-name b) id)
  (try
    (start b id)
    (build/with-build-log-ns b
      (when (should-run-build? b)
        (do-build* b)))
    b
    (catch Exception e
      (println "run-build: except:" b e)
      (error e (format "caught exception on %s" (build/build-name b)))
      (println "assoc'ing failed=true")
      (dosync
       (alter b assoc :failed true)
       (alter b assoc :infrastructure_fail true))
      (throw e))
    (finally
     (finished b)

      ;; Send build notifications, but don't let it fuck up anbything else.
     (straight-jacket
      (when (should-run-build? b)
        (notify/notify-build-results b)))

     (log-result b)
     (when (and (-> @b :failed) cleanup-on-failure)
       (cleanup-nodes b)))))
