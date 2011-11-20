(ns circle.backend.build
  "Main definition of the Build object. "
  (:require [clojure.string :as str])
  (:use [arohner.utils :only (inspect)])
  (:use [circle.util.except :only (throw-if-not)]
        [circle.util.args :only (require-args)])
  (:use [circle.util.model-validation :only (valid?)])
  (:use [circle.util.model-validation-helpers :only (is-ref?)])
  (:use [clojure.tools.logging :only (log)]))

(def build-defaults {:continue? true
                     :num-nodes 1
                     :action-results []})

(defn build [{:keys [project-name ;; string
                     build-num    ;; int
                     vcs-url
                     vcs-revision ;; if present, the commit that caused the build to be run, or nil
                     aws-credentials ;; map containing :user and :password
                     r53-zone-id ;; zone-id of the domain we're managing. Required for DNS updates.
                     notify-email ;; a seq of email addresses to notify when build is done
                     repository
                     commits
                     actions      ;; a seq of actions
                     action-results
                     node         ;; Map containing keys required by ec2/start-instance
                     lb-name      ;; name of the load-balancer to use
                     continue?    ;; if true, continue running the build. Failed actions will set this to false
                     start-time
                     stop-time]
              :as args}]
  (require-args project-name build-num vcs-url actions)
  (ref (merge build-defaults args)))

(defn extend-group-with-revision
  "update the build, setting the pallet group-name to extends the
  existing group with the VCS revision."
  [build]
  (dosync
   (alter build
          assoc-in [:group :group-name] (keyword (.toLowerCase (format "%s-%s" (-> @build :project-name) (-> @build :vcs-revision))))))
  build)

(defn build-name
  ([build]
     (build-name (-> @build :project-name) (-> @build :build-num)))
  ([project-name build-num]
     (str project-name "-" build-num)))

(defn checkout-dir
  "Directory where the build will be checked out, on the build box."
  ([build]
     (checkout-dir (-> @build :project-name) (-> @build :build-num)))
  ([project-name build-num]
     (str/replace (build-name project-name build-num) #" " "")))

(defn successful? [build]
  (and (-> @build :stop-time)
       (-> @build :continue?)))

(def ^{:dynamic true
       :doc "A map of environment variables that will be set when commands are run"} *env* {})

(defn log-ns
  "returns the name of the logger to use for this build "
  [build]
  (symbol (str "circle.build." (-> @build :project-name) "-" (-> @build :build-num))))

(def ^:dynamic *log-ns* nil) ;; contains the name of the logger for the current build

(defmacro with-build-log [build & body]
  `(binding [*log-ns* (log-ns ~build)]
     ~@body))

(defn build-log [message & args]
  (when *log-ns*
    (log *log-ns* :info nil (apply format message args))))

(defn build-log-error [message & args]
  (when *log-ns*
    (log *log-ns* :error nil (apply format message args))))