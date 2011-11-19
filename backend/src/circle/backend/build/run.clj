(ns circle.backend.build.run
  (:require [circle.backend.build.email :as email])
  (:require [clj-time.core :as time])
  (:require [circle.backend.build :as build])
  (:use [arohner.utils :only (inspect fold)])
  (:require [circle.env :as env])
  (:use [circle.backend.action :as action])
  (:use [circle.backend.action.nodes :only (cleanup-nodes)])
  (:use [circle.backend.nodes :only (node-info)])
  (:use [circle.logging :only (add-file-appender)])
  (:use [circle.util.except :only (throw-if throw-if-not)])
  (:use [clojure.tools.logging :only (with-logs error infof errorf)]))

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
    (infof "Build %s successful" (build/build-name b))
    (errorf "Build %s failed" (build/build-name b))))

(defn run-build [b & {:keys [cleanup-on-failure]
                          :or {cleanup-on-failure true}}]
  (def last-build b)
  (infof "starting build: %s" (build/build-name b))
  (when (= :deploy (:type b))
    (throw-if-not (:vcs-revision b) "version-control revision is required for deploys"))
  (try
    ;; pwd/with-pwd (build/build-dir b)
    (build/with-build-log b
      (do-build* b)
      (email/notify-build-results b))
    b
    (catch Exception e
      (dosync (alter b assoc :failed? true))
      (error e (format "caught exception on %s %s" (-> b :project-name) (-> b :build-num)))
      (when env/production?
        (email/send-build-error-email b e))
      (throw e))
    (finally
     (log-result b)
     ;;(finished b)
     (when (and (-> @b :failed?) cleanup-on-failure) 
       (cleanup-nodes b)))))

