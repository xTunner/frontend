(ns circle.backend.build.run
  (:require [circle.backend.build.email :as email])
  (:require [circle.backend.build :as build])
  (:use [arohner.utils :only (inspect fold)])
  (:require [circle.env :as env])
  (:require [clj-time.core :as time])
  (:use [circle.backend.action :as action])
  (:use [circle.backend.action.nodes :only (cleanup-nodes)])
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
   (throw-if (-> @b :start_time) "refusing to run started build")
   (build/add-to-db b id)
   (alter in-progress conj b)))

(defn finished [b]
  (dosync
   (infof "removing build %s from in-progress" (build/build-name b))
   (alter in-progress disj b)
   (alter b assoc :stop_time (-> (time/now) .toDate)))
  (build/update-mongo b))

(defn run-build [b & {:keys [cleanup-on-failure id]
                          :or {cleanup-on-failure true}}]
  (infof "starting build: %s, %s" (build/build-name b) id)
  (try
    (start b id)
    (build/with-build-log-ns b
      (do-build* b)
      (finished b)
      (email/notify-build-results b))
    b
    (catch Exception e
      (println "run-build: except:" b e)
      (error e (format "caught exception on %s" (build/build-name b)))
      (println "assoc'ing failed=true")
      (dosync
       (alter b assoc :failed true))
      (email/send-build-error-email b e)
      (throw e))
    (finally
     (log-result b)

     (when (and (-> @b :failed) cleanup-on-failure)
       (cleanup-nodes b)))))
