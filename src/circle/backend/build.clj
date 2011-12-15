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
  (:require [circle.sh :as sh])
  (:use [clojure.tools.logging :only (log)]))

(def build-coll :builds) ;; mongo collection for builds

(def build-defaults {:continue? true
                     :num-nodes 1
                     :action-results []})

(def node-validation
  [(require-keys [:username])])

(def build-validations
  [(require-keys [:_project_id
                  :build_num
                  :vcs_url
                  :vcs_revision
                  :node])
   (fn [build]
     (v/validate node-validation (-> build :node)))
   (fn [b]
     (when (and (= :deploy (:type b)) (not (-> b :vcs_revision)))
       "version-control revision is required for deploys"))
   (fn [b]
     (when-not (and (-> b :build_num (integer?))
                    (-> b :build_num (pos?)))
       "build_num must be a positive integer"))])

(defn validate [b]
  (v/validate build-validations b))

(defn valid? [b]
  (v/valid? build-validations b))

(defn validate!
  "Validates the contents of a build. i.e. pass the map, not the ref"
  [b]
  {:pre [(not (ref? b))]}
  (v/validate! build-validations b))

(def build-dissoc-keys
  ;; Keys on build that shouldn't go into mongo, for whatever reason
  [:actions :action-results])

(defn insert! [b]
  (let [return (mongo/insert! build-coll (apply dissoc @b build-dissoc-keys))]
    (dosync
     (alter b assoc :_id (-> return :_id)))
    b))

(defn update-mongo
  "Given a build ref, update the mongo row with the current values of b."
  [b]
  (assert (-> @b :_id))
  (c-mongo/ensure-object-id-ref build-coll b)
  (mongo/update! build-coll
                 {:_id (-> @b :_id)}
                 (apply dissoc @b build-dissoc-keys)))

(defn find-build-by-name
  "Returns a build obj, or nil"
  [project build-num])

(defn build [{:keys [project_name ;; string
                     build_num    ;; int
                     vcs_url
                     vcs_revision ;; if present, the commit that caused the build to be run, or nil
                     aws_credentials ;; map containing :user and :password
                     notify_emails ;; a seq of email addresses to notify when build is done
                     repository
                     commits
                     actions      ;; a seq of actions
                     action-results
                     node         ;; Map containing keys required by ec2/start-instance
                     lb-name      ;; name of the load-balancer to use
                     continue?    ;; if true, continue running the build. Failed actions will set this to false
                     start_time
                     stop_time]
              :as args}]
  (let [project (project/get-by-url! vcs_url)
        build_num (or build_num (project/next-build-num project))]
    (ref (merge build-defaults
                args
                {:build_num build_num
                 :_project_id (-> project :_id)})
         :validator validate!)))

(defn extend-group-with-revision
  "update the build, setting the pallet group-name to extends the
  existing group with the VCS revision."
  [build]
  (dosync
   (alter build
          assoc-in [:group :group-name] (keyword (.toLowerCase (format "%s-%s" (-> @build :project_name) (-> @build :vcs_revision))))))
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
     (str/replace (build-name project-name build-num) #" " "-")))

(defn successful? [build]
  (and (-> @build :stop_time)
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

(defn build-with-instance-id
  "Returns the build from the DB with the given instance-id"
  [id]
  (mongo/fetch-one build-coll :where {:instance-ids id}))

(defn ssh
  "Opens a terminal window that SSHs into intance with the provided id.

Assumes:
  1) the instance was started by a build
  2) the build is in the DB
  3) the instance is still running
  4) this clojure process is on OSX"

  [instance-id]
  (let [build (build-with-instance-id instance-id)
        ssh-private-key (-> build :node :private-key)
        username (-> build :node :username)
        ip-addr (-> build :node :ip-addr)
        key-temp-file (fs/tempfile "ssh")
        _ (spit key-temp-file ssh-private-key)
        _ (fs/chmod "-r" key-temp-file)
        _ (fs/chmod "u+r" key-temp-file)
        ssh-cmd (format "ssh -i %s %s@%s" key-temp-file username ip-addr)
        tell-cmd (format "'tell app \"Terminal\" \ndo script \"%s\"\n end tell'" ssh-cmd)]
    (sh/shq (osascript -e ~tell-cmd))))