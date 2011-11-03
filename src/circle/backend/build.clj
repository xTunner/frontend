(ns circle.backend.build
  (:use [arohner.utils :only (inspect)])
  (:use [circle.utils.except :only (throw-if-not)]
        [circle.utils.args :only (require-args)])
  (:use [clojure.tools.logging :only (log)]))

(def build-defaults {:continue? true
                     :num-nodes 1
                     :action-results []})

(defn build [& {:keys [project-name ;; string
                       build-num ;; int
                       vcs-type
                       vcs-url
                       vcs-revision ;; if present, the commit that caused the build to be run, or nil
                       aws-credentials ;; map containing :user and :password
                       r53-zone-id   ;; zone-id of the domain we're managing. Required for DNS updates.
                       notify-email ;; an email address to notify when build is done
                       actions ;; a seq of actions
                       action-results
                       group ;; the pallet group spec to use for the build
                       num-nodes ;; number of nodes to start/use
                       nodes ;; nodes started by this build. Keep track so we can clean them up
                       lb-name   ;; name of the load-balancer to use
                       continue?  ;; if true, continue running the build. Failed actions will set this to false
                       start-time
                       stop-time]
                :as args}]
  (require-args project-name build-num vcs-type vcs-url actions)
  (ref (merge build-defaults args)))

(defn extend-group-with-revision
  "update the build with a new group that extends the existing group at the :group key."
  [build]
  (dosync
   (alter build
          assoc-in [:group :group-name] (keyword (.toLowerCase (format "%s-%s" (-> @build :project-name) (-> @build :vcs-revision))))))
  build)

(defn build-name [build]
  (str (-> @build :project-name) "-" (-> @build :build-num)))

(defn successful? [build]
  (and (-> @build :stop-time)
       (-> @build :continue?)))

(def ^{:dynamic true
       :doc "present working directory on the build box commands will run in"} *pwd* "")

(defmacro with-pwd
  "When set, each command will start in the specified directory. Dir is a string."
  [dir & body]
  `(binding [*pwd* ~dir]
     ~@body))

(defn log-ns
  "returns the name of the logger to use for this build "
  [build]
  (symbol (str "circle.build." (-> @build :project-name) "-" (-> @build :build-num))))

(def ^:dynamic *log-ns* nil) ;; contains the name of the logger for the current build

(defmacro with-build-log [build & body]
  `(binding [*log-ns* (log-ns ~build)]
     ~@body))

(defn build-log [message & args]
  (throw-if-not *log-ns* "Log NS is not set")
  (log *log-ns* :info nil (apply format message args)))

(defn build-log-error [message & args]
  (throw-if-not *log-ns* "Log NS is not set")
  (log *log-ns* :error nil (apply format message args)))