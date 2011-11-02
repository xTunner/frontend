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
        (build/build-log "running" (-> act :name))
        (action/with-action build act
          (-> act :act-fn (.invoke build))))))
  (stop* build)
  build)

(defn log-result [b]
  (if (build/successful? b)
    (infof "Build %s successful" (build/build-name b))
    (errorf "Build %s failed" (build/build-name b))))

(defn run-build [build & {:keys [cleanup-on-failure]
                          :or {cleanup-on-failure true}}]
  (def last-build build)
  (infof "starting build: %s" (build/build-name build))
  (when (= :deploy (:type build))
    (throw-if-not (:vcs-revision build) "version-control revision is required for deploys"))
  (try
    (build/with-pwd "" ;; bind here, so actions can set! it
      (build/with-build-log build
        (do-build* build)
        (when (-> @build :notify-email)
          (email/send-build-email build))))
    build
    (catch Exception e
      (dosync (alter build assoc :failed? true))
      (error e (format "caught exception on %s %s" (-> build :project-name) (-> build :build-num)))
      (when env/production?
        (email/send-build-error-email build e))
      (throw e))
    (finally
     (log-result build)
     (when (and (-> @build :failed?) cleanup-on-failure) 
       (errorf "terminating nodes")
       (cleanup-nodes build)))))