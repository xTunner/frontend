(ns circle.backend.build.config
  "Functions for reading and parsing the build config"
  (:use [clojure.core.incubator :only (-?>)])
  (:require [circle.backend.ssh :as ssh])
  (:require [clojure.string :as str])
  (:require [circle.backend.build :as build])
  (:require [circle.model.project :as project])
  (:require [circle.model.spec :as spec])
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
  (:use [circle.util.map :only (rename-keys)])
  (:require [clj-yaml.core :as yaml])
  (:import java.io.StringReader)
  (:use [arohner.utils :only (inspect)])
  (:use [circle.util.except :only (eat)]))

(defn parse-config
  "Takes the unparsed config file contents."
  [config]
  (-?> config
      (StringReader.)
      yaml/parse-string))

(defn validate-config
  "validate the semantics of the config"
  [c]
  true)

(defn load-config [path]
  (let [config (-> path
                   (slurp)
                   (eat))]
    (when config
      (parse-config config))))

(defn db-config [commands]
  {:nodes {:www
           {:ami (-> circle.backend.nodes.rails/rails-node :ami)
            :instance-type "m1.small"
            :username "ubuntu"
            :security-groups ["www"]
            :availability-zone "us-east-1a"}}
   :jobs {:build
          {:template :build
           :node :www
           :commands commands}}
   :schedule {:commit
              {:job :build}}})

(defn spec-commands [spec]
  (->> spec
       ((juxt :setup :dependencies :compile :test))
       (mapcat (fn [line]
                 (str/split (or line "") #"\r\n")))
       (filter #(> (.length %) 0))))

(defn get-config-from-db [url]
  (let [project (project/get-by-url url)]
    (when-let [spec (spec/get-spec-for-project project)]
      (when-let [commands (seq (spec-commands spec))]
        (db-config commands)))))

(defn get-config-for-url
  "Given the canonical git URL for a repo, find and return the config file. Clones the repo if necessary."
  [url & {:keys [vcs-revision]}]
  {:pre [url]}
  (let [ssh-key (project/ssh-key-for-url url)
        repo (git/default-repo-path url)
        git-url (if (github/github? url)
                  (github/->ssh url)
                  url)]
    (git/ensure-repo git-url :ssh-key ssh-key :path repo)
    (when vcs-revision
      (git/checkout repo vcs-revision))
    (or
     (get-config-from-db url)
     (-> repo
        (fs/join "circle.yml")
        (load-config)))))

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

(defn validate-job [job]
  (validate! [(require-keys [:template])] job))

(defn parse-actions [job]
  (doall (map parse-action (-> job :commands))))

(defn load-actions
  "Finds job in config, loads approprate template and parses actions"
  [job]
  (let [template-name (-> job :template)
        actions (parse-actions job)]
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

(defn translate-email-recipient
  "Translates an email recipient like :committer to an email address"
  [github-json to]
  (condp = to
    :owner (-?> github-json :repository :owner :email (vector))
    :committer (->> (-> github-json :commits)
                    (map (fn [c]
                           (-> c :author :email))))))

(defn get-build-email-recipients
  "Takes a seq of email notifiers, like :committer. returns email literals"
  [notifies github-json]
  (->> notifies
       (mapcat #(translate-email-recipient github-json %))
       (set)))

(defn add-project-info
  "Adds a bunch of relevant information from the project to the build
  obj. All arguments can be overridden with keyword args. Proto-build
  is a map that hasn't been turned into a build yet"
  [project proto-build & {:as opts}]
  (merge
   {:vcs_url (-> project :vcs_url)
    :lb-name (-> project :lb-name)
    :vcs-private-key (-> project :ssh_private_key)
    :vcs-public-key (-> project :ssh_public_key)}
   proto-build
   opts))

(defn build-from-config [config project & {:keys [vcs-revision job-name build_num notify]}]
  (let [job-name (or job-name (-> config :jobs (first) (key)))
        job (load-job config job-name)
        node (load-node config job (-> project :vcs_url))
        schedule (-> config :schedule)
        actions (load-actions job)
        url (-> project :vcs_url)
        repo (git/default-repo-path url)
        vcs-revision (or vcs-revision (git/latest-local-commit repo))]
    (assert job-name)
    (->>
     {:notify_emails notify
      :build_num build_num
      :vcs_revision vcs-revision
      :job-name job-name
      :node node
      :actions actions}
     (add-project-info project)
     (build/build))))

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

(defn infer-build-from-url
  "Assumes url does not have a circle.yml. Examine the source tree, and return a build"
  [url]
  (let [project (project/get-by-url url)
        project-name (or (-> project :name) (infer-project-name url))
        repo (git/default-repo-path url)
        vcs-revision (git/latest-local-commit repo)
        node (inference/node repo)]
    (build/build
     (add-project-info project
                       {:vcs_url url
                        :vcs_revision vcs-revision
                        :node node
                        :actions (inference/infer-actions repo)
                        :job-name "build-inferred"
                        :notify_emails ["founders@circleci.com"] ;; don't use :committer yet, these people might not be using circle
                        }))))

(defn build-from-url
  "Given a project url and a build name, return a build. Helper method for repl"
  [url & {:keys [job-name vcs-revision]}]
  (let [project (project/get-by-url! url)
        config (get-config-for-url url :vcs_revision vcs-revision)]
    (if (and config project)
      (build-from-config config project
                         :vcs_revision vcs-revision
                         :notify ["founders@circleci.com"] ;; (-> config :jobs job-name :notify_emails (parse-notify) (get-build-email-recipients github-json))
                         :job-name job-name)
      (infer-build-from-url url))))

(defn build-from-json
  "Given a parsed github commit hook json, return a build that needs to be run, or nil"
  [github-json]
  (let [url (-> github-json :repository :url)
        vcs-revision (-> github-json :after)]
    (build-from-url url :vcs_revision vcs-revision)))
