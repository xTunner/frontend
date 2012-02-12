(ns circle.backend.build.run
  (:require [circle.backend.build.notify :as notify])
  (:require [circle.model.build :as build])
  (:use [arohner.utils :only (fold)])
  (:require [circle.env :as env])
  (:require [clj-time.core :as time])
  (:use [circle.util.time :only (java-now)])
  (:use circle.util.straight-jacket)
  (:use [circle.globals :only (*current-build-url* *current-build-number*)])
  (:require [circle.backend.action :as action])
  (:use [circle.backend.action.nodes :only (cleanup-nodes)])
  (:require [circle.backend.build.config :as config])
  (:require [circle.model.project :as project])
  (:use [circle.logging :only (add-file-appender)])
  (:use [circle.util.except :only (throw-if throw-if-not)])
  (:use [circle.util.seq :only (find-first index-of)])
  (:use [clojure.tools.logging :only (with-logs error infof errorf)]))

(def in-progress (ref #{}))

(defn finish-action [b act]
  (dosync
   ;; TODO - make this cleaner somehow, remove the need for index-of
   (let [act-index (index-of (-> @b :actions) act)]
     (throw-if-not (integer? act-index) "couldn't find action")
     (alter b update-in [:actions act-index] assoc :finished true))))

(defn run-action [b act]
  (build/build-log "running %s" (-> act :name))
  (try
    (action/run-action b act)
    (finally
     (finish-action b act)
     (build/update-mongo b))))

(defn next-act
  "Returns the next action to run"
  [b]
  (find-first #(not (:finished %)) (-> @b :actions)))

(defn do-build* [b]
  (while (and (-> @b :continue?)
              (next-act b))
    (run-action b (next-act b)))
  b)

(defn log-result [b]
  (if (build/successful? b)
    (infof "Build %s successful" (build/build-name b))
    (errorf "Build %s failed" (build/build-name b))))

(defn start [b]
  (throw-if-not (-> @b :_id) "build must have an id")
  (infof "starting build: %s, %s" (build/build-name b) (-> @b :_id))
  (dosync
   (alter b assoc :start_time (java-now))
   (alter in-progress conj b))
  (build/update-mongo b))

(defn finished [b]
  (dosync
   (infof "removing build %s from in-progress" (build/build-name b))
   (alter in-progress disj b)
   (alter b assoc :stop_time (-> (time/now) .toDate)))
  (let [project (build/get-project b)]
    (when (and (-> @b :actions (count) (zero?)))
      (project/set-uninferrable project)))
  (build/update-mongo b))

(defn run-build [b & {:keys [cleanup-on-failure]
                      :or {cleanup-on-failure true}}]
  (binding [*current-build-url* (-> @b :vcs_url)
            *current-build-number* (-> @b :build_num)]
    (straight-jacket
     (try
       (start b)
       (build/with-build-log-ns b
         (do-build* b))
       b
       (catch Exception e
         (println "run-build: except:" b e)
         (error e (format "caught exception on %s" (build/build-name b)))
         (dosync
          (alter b assoc :failed true)
          (alter b assoc :infrastructure_fail true))
         (throw e))
       (finally
        (finished b)

        ;; Send build notifications, but don't let it fuck up anything else.
        (straight-jacket
         (notify/notify-build-results b))

        (log-result b)
        (when (and (-> @b :failed) cleanup-on-failure)
          (cleanup-nodes b)))))))

(defn configure
  "Makes sure the build has run it's configure step (if it has one). Mainly a convenience for testing."
  [build]
  (let [first-act (-> @build :actions (first))]
    (if (and (= config/config-action-name (-> first-act :name))
             (not (-> first-act :finished)))
      (run-action build first-act)))
  build)
