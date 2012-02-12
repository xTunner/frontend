(ns circle.backend.build.inference.rails
  (:use [clojure.core.incubator :only (-?>)])
  (:require [clojure.string :as str])
  (:require [circle.model.build :as build])
  (:require clj-yaml.core)
  (:require [circle.sh :as sh])
  (:require fs)
  (:use [clojure.tools.logging :only (errorf)])
  (:use [circle.backend.action.bash :only (bash)])
  (:use [circle.backend.action :only (defaction action)])
  (:use circle.util.fs)
  (:use [circle.util.core :only (re)])
  (:require [circle.util.map :as map])
  (:use [circle.util.seq :only (find-first)])
  (:require circle.backend.nodes.rails)
  (:require [circle.backend.build.inference.mysql :as mysql])
  (:use [circle.backend.build.inference.gems-map])
  (:import (org.yaml.snakeyaml Yaml DumperOptions))
  (:require [circle.backend.build.inference.postgres :as postgres]))

(defn bundler?
  "True if this project is using bundler"
  [repo]
  (fs/exists? (fs/join repo "Gemfile")))

(defn dot-gems? [repo]
  (fs/exists? (fs/join repo ".gems")))

(defn gemfile.lock?
  [repo]
  (fs/exists? (fs/join repo "Gemfile.lock")))

(defn database-yml?
  "True if the database.yml is at the exact location config/database.yml"
  [repo]
  (-> (fs/join repo "config" "database.yml")
      (fs/exists?)))

(defn dir-contains-ruby-files? [dir]
  (dir-contains-files? dir #"^.*\.rb$"))

(defn rspec?
  "True if this project has rspec tests that need to be run"
  [repo]
  (-> (fs/join repo "spec")
      (dir-contains-ruby-files?)))

(defn rvm? [repo]
  (-> (fs/join repo ".rvmrc")
      (fs/exists?)))

(defn migrations? [repo]
  (-> (fs/join repo "db" "migrate")
      (dir-contains-files?)))

(defn test-unit? [repo]
  (-> (fs/join repo "test")
      (dir-contains-ruby-files?)))

(defn schema-rb? [repo]
  (-> (fs/join repo "db" "schema.rb")
      (fs/exists?)))

(defn using-gem? [repo name]
  (re-file? (fs/join repo "Gemfile.lock") (re (format " %s " name))))

(defn gem-version
  "Attempts to find the version of the gem."
  [repo gem-name]
  (let [file-path (fs/join repo "Gemfile.lock")]
    (when (fs/exists? file-path)
      (let [gem-contents (slurp file-path)]
        (-?> (re-find (re (format "\\s+%s \\(([0-9.]+)\\)" gem-name)) gem-contents)
           (second)
           (str/trim))))))

(defn data-mapper? [repo]
  (using-gem? repo "dm-rails"))

(defn cucumber? [repo]
  (dir-contains-files? (fs/join repo "features") #".*\.feature$"))

(defn jasmine? [repo]
  (using-gem? repo "jasmine"))

(defn get-database-yml
  [repo]
  (when (database-yml? repo)
    (fs/join repo "config" "database.yml")))

(defn first-db-yml-gem [repo]
  (find-first #(using-gem? repo %) database-yml-gems))

(defn using-db-yml-gem?
  [repo]
  (boolean (seq (first-db-yml-gem repo))))

(defn need-database-yml? [repo]
  (and (not (database-yml? repo))
       (using-db-yml-gem? repo)))

(defn generate-database-yml-str [repo]
  (let [options (doto (DumperOptions.)
                  (.setIndent 2)
                  (.setDefaultFlowStyle org.yaml.snakeyaml.DumperOptions$FlowStyle/BLOCK)
                  (.setPrettyFlow true))
        yaml (org.yaml.snakeyaml.Yaml. options)
        adapter (gem-adapter-map (first-db-yml-gem repo))]
    (->> {:test
          {:adapter adapter
           :database "circle_test"
           :username "circle"
           :password nil}}
         ((var clj-yaml.core/stringify))
         (.dump yaml))))

(defn generate-database-yml [repo]
  (let [db-yml-str (generate-database-yml-str repo)]
    (bash (sh/q (echo ~db-yml-str > config/database.yml))
          :name "Generate database.yml"
          :type :setup)))

(defn parse-db-yml [repo]
  (try
    (-?> (get-database-yml repo) (slurp) (clj-yaml.core/parse-string))
    (catch org.yaml.snakeyaml.scanner.ScannerException e
      (errorf "failed to parse %s" (get-database-yml repo)))))

(defn need-mysql-socket? [repo]
  (let [db-config (parse-db-yml repo)]
    (-> db-config :test :adapter #{"mysql2" "mysql"} boolean)))

(defn mysql-socket-path [repo]
  (let [db-config (parse-db-yml repo)]
    (-> db-config :test :socket)))

(defn ensure-db-user
  "Returns an action that creates a DB user & password when necessary, or nil"
  [repo]
  (let [db-info (-> repo (parse-db-yml) :test)
        db-type (-> db-info :adapter)]
    (when db-info
      (condp = db-type
        "mysql" (mysql/create-user db-info)
        "mysql2" (mysql/create-user db-info)
        "postgresql" (postgres/create-role db-info)
        nil))))

(def ^{:dynamic true} use-bundler? true)

(defmacro bundler-cmd
  "Takes a one-line stevedore. prepends 'bundle exec' if we're using bundler"
  [body & {:keys [environment]}]
  `(if use-bundler?
     (sh/q (~'bundle ~'exec ~@body))
     (sh/q ~body)))

(defn rspec-test
  "action to run the rspec tests"
  [repo]
  (let [rspec-version (gem-version repo "rspec")
        rspec-1? (= (nth rspec-version 0) \1) ;; if the rspec version starts with "1"
        rspec-cmd (if rspec-1? 'spec 'rspec)]
    (bash (bundler-cmd (~rspec-cmd "spec"))
          :environment {:RAILS_ENV :test}
          :type :test)))

(defmacro rake
  "Returns an action to run a single rake command"
  [body & {:keys [type]}]
  `(bash (bundler-cmd (~'rake ~body ~'--trace))
         :environment {:RAILS_ENV :test}
         :type ~type))

(defmacro rvm
  "Returns an action to run rvm"
  [& args]
  `(bash (sh/q (~'rvm ~@args))
         :type :setup))

(defn cucumber-test
  []
  (rake cucumber :type :test))

(defn bundle-install []
  (bash (sh/q (bundle install))
        :type :setup
        :environment {:RAILS_GROUP :test}
        :name "bundle install"))

(defn sed-gem [gem-name]
  (let [sed-str (format "/gem [\\'\\\"]%s[\\'\"]/ d" gem-name)]
    (sh/q (sed -i ~(format "\"%s\"" sed-str) Gemfile))))

(defn blacklist []
  (bash (sh/q (doseq [g ~blacklisted-gems]
                ~(sed-gem "$g")))
        :name "blacklist problematic gems"
        :type :setup))

(defn spec
  "Returns the set of actions necessary for this project"
  [repo]
  (let [use-bundler? (bundler? repo)
        has-db-yml? (or (database-yml? repo) (need-database-yml? repo))]
    (binding [use-bundler? (bundler? repo)]
      (->>
       [(when (dir-contains-ruby-files? repo)
          (if (rvm? repo)
            (rvm rvmrc trust ~repo)
            (rvm use "1.9.2" --default)))
        (when use-bundler?
          (blacklist))
        (when use-bundler?
          (bundle-install))
        (when (need-database-yml? repo)
          (generate-database-yml repo))
        (when (need-mysql-socket? repo)
          (mysql/ensure-socket (mysql-socket-path repo)))
        (ensure-db-user repo)
        (when has-db-yml?
          (rake db:create :type :setup))
        (cond
         (data-mapper? repo) (rake db:automigrate :type :setup)
         (schema-rb? repo) (rake db:schema:load :type :setup)
         (migrations? repo) (rake db:migrate :type :setup)
         :else nil)
        (when (rspec? repo)
          (rspec-test repo))
        (when (cucumber? repo)
          (rake cucumber :type :test))
        (when (jasmine? repo)
          (rake jasmine:ci :type :test))
        (when (test-unit? repo)
          (rake test :type :test))]
       (filter identity)))))
