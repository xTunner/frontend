(ns circle.backend.build.run
  (:require [circle.backend.build.email :as email])
  (:require [clj-time.core :as time])
  (:require [circle.backend.build :as build])
  (:use [arohner.utils :only (inspect fold)])
  (:require [circle.env :as env])
  (:use [circle.backend.action :as action])
  (:use [circle.backend.action.bash :only (with-pwd)])
  (:use [circle.backend.action.nodes :only (cleanup-nodes)])
  (:use [circle.backend.nodes :only (node-info)])
  (:use [circle.logging :only (add-file-appender)])
  (:use [circle.utils.except :only (throw-if throw-if-not)])
  (:use [clojure.tools.logging :only (with-logs error infof errorf)]))

(defn log-ns
  "returns the name of the logger to use for this build "
  [build]
  (str "circle.build." (-> @build :project-name) "-" (-> @build :build-num)))

(defn start* [build]
  (dosync
   (alter build assoc :start-time (time/now))))

(defn stop* [build]
  (dosync
   (alter build assoc :stop-time (time/now))))

(defn do-build* [build]
  (throw-if (-> @build :start-time) "refusing to run started build")
  (start* build)
  (doseq [act (-> @build :actions)]
    (when (-> @build :continue?)
      (let [current-act-results-count (count (-> @build :action-results))]
        (println "running" (-> act :name))
        (action/with-action build act
          (-> act :act-fn (.invoke build))))))
  (stop* build)
  build)

(defn run-build [build & {:keys [cleanup-on-failure]
                          :or {cleanup-on-failure true}}]
  (def last-build build)
  (infof "starting build: %s #%s" (-> build :project-name) (-> build :build-num))
  
  (when (= :deploy (:type build))
    (throw-if-not (:vcs-revision build) "version-control revision is required for deploys"))
  
  (try
    (with-pwd "" ;; bind here, so actions can set! it
      (with-logs (log-ns build)
        (do-build* build)
        (when (-> build :notify-email)
          (email/send-build-email build))
        build))    
    (catch Exception e
      (dosync (alter build assoc :failed? true))
      (error e (format "caught exception on %s %s" (-> build :project-name) (-> build :build-num)))
      (when env/production?
        (email/send-build-error-email build e))
      (throw e))
    (finally
     (when (and (-> @build :failed?) cleanup-on-failure) 
       (errorf "terminating nodes")
       (cleanup-nodes build)))))