(ns circle.backend.build.config
  "Functions for reading and parsing the build config"
  (:require [circle.backend.build :as build])
  (:require [circle.model.project :as project])
  (:require [circle.backend.github-url :as github])
  (:require [circle.backend.ec2 :as ec2])
  (:require [circle.backend.git :as git])
  (:require [circle.backend.build.template :as template])
  (:require circle.backend.build.nodes.rails)
  (:use [clojure.tools.logging :only (infof error)])
  (:use [circle.backend.action.bash :only (bash)])
  (:use [circle.util.model-validation :only (validate!)])
  (:use [circle.util.model-validation-helpers :only (is-map? require-predicate require-keys allow-keys)])
  (:use [circle.util.core :only (apply-if)])
  (:use [circle.util.except :only (assert! throw-if-not)])
  (:use [circle.util.map :only (rename-keys)])
  (:require [clj-yaml.core :as yaml])
  (:import java.io.StringReader)
  (:use [arohner.utils :only (inspect)])
  (:use [circle.util.except :only (eat)]))

(defn parse-config
  "Takes the unparsed config file contents."
  [config]
  (-> config
      (StringReader.)
      yaml/parse-string))

(defn validate-config
  "validate the semantics of the config"
  [c]
  true)

(defn load-config [path]
  (-> path
      (slurp)
      (eat)
      (parse-config)))

(defn get-config-for-url
  "Given the canonical git URL for a repo, find and return the config file. Clones the repo if necessary."
  [url]
  (let [ssh-key (project/ssh-key-for-url url)
        repo (git/default-repo-path url)
        git-url (if (github/github? url)
                  (github/->ssh url)
                  url)]
    (git/ensure-repo git-url :ssh-key ssh-key :path repo)
    (-> repo
        (fs/join "circle.yml")
        (load-config))))

(defn validate-action-map [cmd]
  (validate! [(is-map?)
              (require-predicate (fn [cmd]
                                   (= 1 (count cmd))))
              (fn [cmd]
                ((allow-keys [:environment :pwd] (format "only :environment and :pwd are allowed in action %s" cmd)) (-> cmd (first) (val))))] cmd))

(defn parse-action-map
  "Converts user command string into a bash action."
  [checkout-dir cmd]
  (validate-action-map cmd)
  (let [body (-> cmd (first) (key))
        body (apply-if (keyword? body) name body)
        env (-> cmd (first) (val) :environment)
        pwd (-> cmd (first) (val) :pwd)
        final-pwd (if pwd
                    (fs/join checkout-dir pwd)
                    checkout-dir)]
    (bash body :environment env :pwd final-pwd)))

(defn parse-action [checkout-dir cmd]
  (cond
   (string? cmd) (bash cmd :pwd checkout-dir)
   (map? cmd) (parse-action-map checkout-dir cmd)))

(defn validate-job [job]
  (validate! [(require-keys [:template])] job))

(defn parse-actions [job checkout-dir]
  (doall (map (partial parse-action checkout-dir) (-> job :commands))))

(defn load-actions
  "Finds job in config, loads approprate template and parses actions"
  [job checkout-dir]
  (let [template-name (-> job :template)
        template (template/find template-name)
        _ (throw-if-not template "could not find template %s" template-name)
        actions (parse-actions job checkout-dir)
        before (map #(apply % []) (-> template :prefix))
        after (map #(apply % []) (-> template :suffix))]
    (concat before actions after)))

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
  [node url]
  (when (-> node :private-key)
    (try
      (let [pub-key-path (-> node :public-key)
            priv-key-path (-> node :private-key)
            pub-key (slurp (fs/join (git/default-repo-path url) pub-key-path))
            priv-key (slurp (fs/join (git/default-repo-path url) priv-key-path))]
        {:private-key priv-key
         :public-key pub-key
         :keypair-name (ec2/ensure-keypair (keypair-name priv-key-path) pub-key)})
      (catch Exception e
        (println "except" e)
        (error e "error loading keys")))))

(defn load-default-node [config]
  circle.backend.build.nodes.rails/default-rails-node)

(defn load-specific-node
  "Load a named node out of the config file"
  [config node-name url]
  (let [node (-> config :nodes node-name)
        node (merge node (load-keys node url))]
    (throw-if-not node "node named %s not found in config" node-name)
    node))

(defn load-node [config job url]
  (let [node-name (-> job :node (keyword))]
    (if node-name
      (load-specific-node config node-name url)
      (load-default-node config))))

(defn build-from-config [config project vcs-revision job-name build-num checkout-dir]
  (let [job (load-job config job-name)
        node (load-node config job (-> project :vcs-url))
        actions (load-actions job checkout-dir)
        notify (-> config :jobs job-name :notify-email (parse-notify))]
    (build/build (merge
                  {:notify-email notify
                   :build-num build-num
                   :vcs-revision vcs-revision
                   :node node
                   :actions actions}
                  (rename-keys {:name :project-name} project)))))

(defn build-from-name
  "Given a project name and a build name, return a build. Helper method for repl"
  [project-name job-name]
  (let [project (project/get-by-name project-name)
        url (-> project :vcs-url)
        config (get-config-for-url url)
        repo (git/default-repo-path url)
        build-num 1
        vcs-revision (git/latest-local-commit repo)
        checkout-dir (build/checkout-dir (-> project :name) build-num)]
    (build-from-config config project vcs-revision job-name build-num checkout-dir)))

(defn build-from-json
  "Given a parsed github commit hook json, return a build that needs to be run, or nil"
  [github-json]
  (let [url (-> github-json :repository :url)
        config (get-config-for-url url)
        _ (when-not config
            (infof "couldn't find config for %s" url))
        project (project/get-by-url! url)
        schedule (-> config :schedule)
        vcs-revision (-> github-json :after)
        build-num 1
        job-name (-> schedule :commit :job (keyword))
        checkout-dir (build/checkout-dir (-> project :name) build-num)]
    (if (and config project)
      (build-from-config config project vcs-revision job-name build-num checkout-dir))))