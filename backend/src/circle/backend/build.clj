(ns circle.backend.build
  "Main definition of the Build object. "
  (:require [clojure.string :as str])
  (:use [arohner.utils :only (inspect)])
  (:use [circle.util.except :only (throw-if-not)]
        [circle.util.args :only (require-args)])
  (:require [circle.util.model-validation :as v])
  (:require [circle.backend.ssh :as ssh])
  (:require [circle.model.project :as project])
  (:use [circle.util.model-validation-helpers :only (is-ref? require-keys)])
  (:use [circle.util.predicates :only (ref?)])
  (:require [somnium.congomongo :as mongo])
  (:require [circle.util.mongo :as c-mongo])
  (:use [clojure.tools.logging :only (log)]))

(def build-coll :build) ;; mongo collection for builds

(def build-defaults {:continue? true
                     :num-nodes 1
                     :action-results []})

(def node-validation
  [(require-keys [:username
                  :public-key
                  :private-key
                  :keypair-name])])

(def build-validations 
  [(require-keys [:project_name
                  :build_num
                  :vcs_url
                  :vcs-revision
                  :node])
   (fn [build]
     (v/validate node-validation (-> build :node)))
   (fn [b]
     (when (and (= :deploy (:type b)) (not (-> b :vcs-revision)))
       "version-control revision is required for deploys"))])

(defn validate [b]
  (v/validate build-validations b))

(defn valid? [b]
  (v/valid? build-validations b))

(defn validate!
  "Validates the contents of a build. i.e. pass the map, not the ref"
  [b]
  {:pre [(not (ref? b))]}
  (v/validate! build-validations b))

(defn ensure-project-id
  "Ensure that the build has a mongo ref to the project"
  [b]
  (when-not (-> @b :_project-id)
    (let [project (project/get-by-url! (-> @b :vcs_url))]
      (dosync
       (alter b assoc :_project-id (-> project :_id)))))
  b)

(defn update-mongo
  "Given a build ref, update the mongo row with the current values of b."
  [b]
  (c-mongo/ensure-object-id-ref build-coll b)
  (ensure-project-id b)
  (mongo/update! build-coll
                 {:_id (-> @b :_id)}
                 (dissoc @b :node :actions :action-results :continue?)))

(defn build [{:keys [project_name ;; string
                     build_num    ;; int
                     vcs_url
                     vcs-revision ;; if present, the commit that caused the build to be run, or nil
                     aws_credentials ;; map containing :user and :password
                     notify_emails ;; a seq of email addresses to notify when build is done
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
  (ref (merge build-defaults args) :validator validate!))

(defn extend-group-with-revision
  "update the build, setting the pallet group-name to extends the
  existing group with the VCS revision."
  [build]
  (dosync
   (alter build
          assoc-in [:group :group-name] (keyword (.toLowerCase (format "%s-%s" (-> @build :project_name) (-> @build :vcs-revision))))))
  build)

(defn build-name
  ([build]
     (build-name (-> @build :project_name) (-> @build :build_num)))
  ([project-name build-num]
     (str project-name "-" build-num)))

(defn checkout-dir
  "Directory where the build will be checked out, on the build box."
  ([build]
     (checkout-dir (-> @build :project_name) (-> @build :build_num)))
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
  (symbol (str "circle.build." (-> @build :project_name) "-" (-> @build :build_num))))

(def ^:dynamic *log-ns* nil) ;; contains the name of the logger for the current build

(defmacro with-build-log [build & body]
  `(binding [*log-ns* (log-ns ~build)
             ssh/handle-out build-log
             ssh/handle-err build-log-error]
     ~@body))

(defn build-log [message & args]
  (when *log-ns*
    (log *log-ns* :info nil (apply format message args))))

(defn build-log-error [message & args]
  (when *log-ns*
    (log *log-ns* :error nil (apply format message args))))