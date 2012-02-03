(ns circle.backend.build.config
  "Functions for reading and parsing the build config"
  (:use [clojure.core.incubator :only (-?>)])
  (:require [circle.backend.ssh :as ssh])
  (:require [clojure.string :as str])
  (:require [circle.model.build :as build])
  (:require [circle.model.project :as project])
  (:require [circle.model.spec :as spec])
  (:require [circle.backend.action :as action])
  (:require [circle.backend.github-url :as github])
  (:require [circle.backend.ec2 :as ec2])
  (:require [circle.backend.git :as git])
  (:require [circle.backend.build.template :as template])
  (:require [circle.backend.build.nodes.rails :as rails])
  (:use [clojure.tools.logging :only (infof error)])
  (:use [circle.backend.action.bash :only (bash)])
  (:use [circle.util.model-validation :only (validate!)])
  (:use [circle.util.model-validation-helpers :only (is-map? require-predicate require-keys allow-keys)])
  (:use [circle.util.core :only (apply-if)])
  (:use [circle.util.except :only (assert! throw-if-not throwf)])
  (:use [clojure.core.incubator :only (-?>)])
  (:require [circle.backend.build.inference :as inference])
  (:require circle.backend.build.inference.rails)
  (:use [circle.util.map :only (rename-keys map-vals)])
  (:require [clj-yaml.core :as yaml])
  (:import java.io.StringReader)
  (:use [arohner.utils :only (inspect)])
  (:use [circle.util.except :only (eat)]))

(defn validate-config
  "validate the semantics of the config"
  [c]
  true)

(defn load-config [path]
  (try
    (-?> path
         slurp
         eat
         StringReader.
         yaml/parse-string)
    (catch Exception e)))

(defn validate-action-map [cmd]
  (validate! [(is-map?)
              (require-predicate (fn [cmd]
                                   (= 1 (count cmd))))
              (fn [cmd]
                ((allow-keys [:environment :pwd] (format "only :environment and :pwd are allowed in action %s" cmd)) (-> cmd (first) (val))))] cmd))

(defn parse-action-map
  "Converts user command string into a bash action."
  [cmd]
  (validate-action-map cmd)
  (let [body (-> cmd (first) (key))
        body (apply-if (keyword? body) name body)
        env (-> cmd (first) (val) :environment)
        pwd (-> cmd (first) (val) :pwd)]
    (bash body :environment env :pwd pwd)))

(defn parse-action [cmd]
  (cond
   (string? cmd) (bash cmd)
   (map? cmd) (parse-action-map cmd)))

(defn parse-spec-actions [spec]
  (->> spec
       (#(select-keys % [:setup :dependencies :compile :test :extra]))
       (map-vals (fn [lines]
                   (->> lines
                        (re-seq #".*")
                        (vec)
                        (remove empty?)
                        (map parse-action))))))

(defn spec-commands [spec]
  (->> spec
       ((juxt :setup :dependencies :compile :test :extra))
       (mapcat (fn [line]
                 (str/split (or line "") #"\r\n")))
       (remove empty?)))


(defn validate-job [job]
  (validate! [(require-keys [:template])] job))

(defn parse-actions [commands]
  (doall (map parse-action commands)))

(defn set-spec [actions]
  (map #(action/set-source % :spec) actions))

(defn load-actions
  "Finds job in config, loads approprate template and parses actions"
  [commands template-name]
  (let [actions (set-spec (parse-actions commands))]
    (template/apply-template template-name actions)))

(defn load-job [config job-name]
  (throw-if-not (keyword? job-name) "job name must be a keyword")
  (let [job (-> config :jobs job-name)]
    (throw-if-not job "could not find job named %s" job-name)
    (validate-job job)
    job))

(defn parse-notify [notifies]
  (for [n notifies]
    (if (re-find #"^:" n)
      (keyword (.substring n 1))
      n)))

(defn keypair-name
  "Infer the name of the keypair given the path to the private key"
  [priv-key-path]
  (let [priv-key-path (fs/basename priv-key-path)
        idx (.lastIndexOf priv-key-path ".")]
    (if (> idx 0)
      (.substring priv-key-path 0 idx)
      priv-key-path)))

(defn load-keys
  "Slurp the file paths and returns a map. Url is the canonical url for the repo. "
  [node repo]
  (when (-> node :private-key)
    (try
      (let [pub-key-path (-> node :public-key)
            priv-key-path (-> node :private-key)
            pub-key (slurp (fs/join repo pub-key-path))
            priv-key (slurp (fs/join repo priv-key-path))]
        {:private-key priv-key
         :public-key pub-key
         :keypair-name (ec2/ensure-keypair (keypair-name priv-key-path) pub-key)})
      (catch Exception e
        (println "except" e)
        (error e "error loading keys")))))

(defn load-specific-node
  "Load a named node out of the config file"
  [config node-name repo]
  (let [node (-> config :nodes node-name)
        node (merge node (load-keys node repo))]
    (throw-if-not node "node named %s not found in config" node-name)
    node))

(defn load-node [config job repo]
  (let [node-name (-> job :node (keyword))]
    (if node-name
      (load-specific-node config node-name repo)
      rails/default-rails-node)))

(defn infer-project-name [url]
  (-> url
      (clj-url.core/parse)
      :path
      (str/split #"/")
      (last)
      (str/replace #"\.git$" "")))

(defn minimal-project [url]
  {:name (infer-project-name url)})

(defn minimal-config [url]
  {})

(defn ensure-checkout
  "Given the canonical git URL for a repo, ensure the repo is checked out."
  [url & {:keys [vcs-revision]}]
  {:pre [url]}
  (let [ssh-key (project/ssh-key-for-url url)
        repo (git/default-repo-path url)
        git-url (if (github/github? url)
                  (github/->ssh url)
                  url)]
    (git/ensure-repo git-url :ssh-key ssh-key :path repo)
    (when vcs-revision
      (git/checkout repo vcs-revision))))


(defn get-db-config [project]
  (when-let [spec (spec/get-spec-for-project project)]
    (when-let [commands (seq (spec-commands spec))]
      {:job-name :build
       :actions (load-actions commands :build)})))

(defn read-yml-config [repo]
  (-> repo
      (fs/join "circle.yml")
      (load-config)))

(defn get-yml-config [repo & {:keys [job-name]}]
  (when-let [config (read-yml-config repo)]
    (let [job-name (or job-name (-> config :jobs (first) (key)))
          job (load-job config job-name)
          node (load-node config job repo)
          actions (load-actions  (-> job :commands) (-> job :template))]
      (assert job-name)
      {:job-name job-name
       :node node
       :actions actions})))

(defn infer-config
  "Assumes url does not have a circle.yml. Examine the source tree, and return a build"
  [repo]
  {:actions (inference/infer-actions repo)
   :job-name "build-inferred"})

(defn build-from-url
  "Given a project url and a build name, return a build. Helper method for repl"
  [url & {:keys [job-name vcs-revision infer]}]
  (ensure-checkout url :vcs-revision vcs-revision)
  (let [repo (git/default-repo-path url)
        vcs-revision (or vcs-revision (git/latest-local-commit repo))
        commit-details (git/commit-details repo vcs-revision)
        project (project/get-by-url! url)

        db-config (get-db-config project)
        yml-config (get-yml-config repo)
        inferred-config (infer-config repo)

        proto-build (cond
                     infer inferred-config
                     yml-config yml-config
                     db-config db-config
                     :default inferred-config)]

    (build/build (merge {:vcs_revision vcs-revision
                         :vcs_url url
                         :lb-name (-> project :lb-name)
                         :vcs-private-key (-> project :ssh_private_key)
                         :vcs-public-key (-> project :ssh_public_key)
                         :node rails/default-rails-node}
                        commit-details
                        proto-build))))

(defn build-from-json
  "Given a parsed github commit hook json, return a build that needs to be run, or nil"
  [github-json]
  (let [url (-> github-json :repository :url)
        vcs-revision (-> github-json :after)]
    (build-from-url url :vcs-revision vcs-revision)))
