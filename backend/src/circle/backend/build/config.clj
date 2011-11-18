(ns circle.backend.build.config
  "Functions for reading and parsing the build config"
  (:require [circle.backend.build :as build])
  (:require [circle.model.project :as project])
  (:require [circle.backend.github-url :as github])
  (:require [circle.backend.git :as git])
  (:require [circle.backend.build.template :as template])
  (:use [clojure.tools.logging :only (infof)])
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

(defn get-config-for-url
  "Given the canonical git URL for a repo, find and return the config file. Clones the repo if necessary."
  [url]
  (let [ssh-key (project/ssh-key-for-url url)
        repo (git/default-repo-path url)
        git-url (if (github/github? url)
                  (github/->ssh url)
                  url)]
    (git/ensure-repo git-url :ssh-key ssh-key :path repo)
    (-> "circle.yml"
        (#(fs/join repo %))
        (slurp)
        (eat)
        (parse-config))))

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
  (let [job (-> config :jobs job-name)]
    (throw-if-not job "could not find job named %s" job-name)
    (validate-job job)
    job))

(defn parse-notify [notifies]
  (for [n notifies]
    (if (re-find #"^:" n)
      (keyword (.substring n 1))
      n)))

(defn build-from-config [config github-json project job-name build-num checkout-dir]
  (let [job (load-job config job-name)
        actions (load-actions job checkout-dir)
        notify (-> config job-name :notify-email (parse-notify))]
    (build/build (merge
                  {:notify-email notify
                   :build-num build-num
                   :actions actions}
                  (rename-keys {:name :project-name} project)))))

(defn builds-for-hook
  "Given a parsed github commit hook json, return a seq of builds that need to be run"
  [github-json]
  (let [url (-> github-json :repository :url)
        config (get-config-for-url url)
        _ (when-not config
            (infof "couldn't find config for %s" url))
        project (project/get-by-url! url)
        schedule (-> config :schedule)
        build-num 1
        job-name (-> schedule :commit :job)
        checkout-dir (build/checkout-dir (-> project :name) build-num)]
    (if (and config project)
      [(build-from-config config github-json project job-name checkout-dir)]
      [])))