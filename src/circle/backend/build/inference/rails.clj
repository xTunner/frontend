(ns circle.backend.build.inference.rails
  (:use [clojure.core.incubator :only (-?>)])
  (:require [clojure.string :as str])
  (:require [circle.model.build :as build])
  (:require [clj-yaml.core])
  (:require [circle.sh :as sh])
  (:require fs)
  (:use [arohner.utils :only (inspect)])
  (:use [clojure.tools.logging :only (errorf)])
  (:use [circle.backend.action.bash :only (bash)])
  (:use [circle.backend.action :only (defaction action)])
  (:use circle.util.fs)
  (:require [circle.util.map :as map])
  (:require circle.backend.nodes.rails)
  (:require [circle.backend.build.inference :as inference])
  (:require [circle.backend.build.inference.mysql :as mysql]))

(defn bundler?
  "True if this project is using bundler"
  [repo]
  (fs/exists? (fs/join repo "Gemfile")))

(defn dot-gems? [repo]
  (fs/exists? (fs/join repo ".gems")))

(defn gemfile.lock?
  [repo]
  (fs/exists? (fs/join repo "Gemfile.lock")))

(defn dir-contains-files? [dir]
  (-> (fs/listdir dir)
      (seq)
      (boolean)))

(defn database-yml?
  "True if the database.yml is at the exact location config/database.yml"
  [repo]
  (-> (fs/join repo "config" "database.yml")
      (fs/exists?)))

(defn rspec?
  "True if this project has rspec tests that need to be run"
  [repo]
  (-> (fs/join repo "spec")
      (dir-contains-files?)))

(defn migrations? [repo]
  (-> (fs/join repo "db" "migrate")
      (dir-contains-files?)))

(defn test-unit? [repo]
  (-> (fs/join repo "test")
      (dir-contains-files?)))

(defn schema-rb? [repo]
  (-> (fs/join repo "db" "schema.rb")
      (fs/exists?)))

(defn re
  "Creates a regex from a string"
  [s]
  (java.util.regex.Pattern/compile s))

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
  (-> (files-matching (fs/join repo "features") #".*\.feature") (seq) (boolean)))

(defn jasmine? [repo]
  (using-gem? repo "jasmine-rails"))

(defn find-database-yml
  "Look in repo/config/ for a file named database.example.yml or similar, and return the path, or nil"
  [repo]
  (let [yml (->> (fs/join repo "config")
                 (fs/listdir)
                 (filter #(and (re-find #"database.*yml" %) (or (re-find #"default" %) (re-find #"example" %))))
                 (first))]
    (when yml
      (fs/join repo "config" yml))))

(defn get-database-yml
  [repo]
  (if (database-yml? repo)
    (fs/join repo "config/database.yml")
    (find-database-yml repo)))

(defn need-cp-database-yml? [repo]
  (and (not (database-yml? repo))
       (find-database-yml repo)))

(defn cp-database-yml
  "Return an action to cp the database.yml to the right spot"
  [repo]
  ;; find-database-yml returns the path relative to the current tree,
  ;; but for a build action it needs to be relative to the checkout
  ;; path
  (let [db-yml-path (->> (find-database-yml repo)
                         (fs/split)
                         (drop 3) ;; drop repos/username/projectname from front of path
                         (apply fs/join))]
    (bash (sh/q (cp ~db-yml-path "config/database.yml"))
          :name "copy database.yml"
          :type :setup)))

(defn parse-db-yml [repo]
  (-?> (get-database-yml repo) (slurp) (clj-yaml.core/parse-string)))

(defn need-mysql-socket? [repo]
  (let [db-config (parse-db-yml repo)]
    (-> db-config :test :adapter (= "mysql"))))

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
        nil))))

(defaction ensure-database-yml []
  {:name "ensuring database.yml exists and is in the proper location"}
  (fn [build]
    (let [repo (build/checkout-dir build)
          db-yml (fs/join repo "database.yml")
          example-yml (find-database-yml repo)]
      (if (and (not (fs/exists? db-yml)) example-yml)
        (fs/copy example-yml db-yml)
        (errorf "couldn't find database.yml for project, things probably aren't going to end well")))))

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

(defn cucumber-test
  []
  (rake cucumber :type :test))

(defn bundle-install []
  (bash (sh/q (bundle install))
        :type :setup
        :environment {:RAILS_GROUP :test}
        :name "bundle install"))

(defn spec
  "Returns the set of actions necessary for this project"
  [repo]
  (let [use-bundler? (bundler? repo)
        found-db-yml (find-database-yml repo)
        has-db-yml? (or (database-yml? repo) (find-database-yml repo))]
    (binding [use-bundler? (bundler? repo)]
      (->>
       [(when use-bundler?
          (bundle-install))
        (when (need-cp-database-yml? repo)
          (cp-database-yml repo))
        (when (need-mysql-socket? repo)
          (mysql/ensure-socket (mysql-socket-path repo)))
        (ensure-db-user repo)
        (when has-db-yml?
          (rake db:create:all :type :setup))
        (cond
         (data-mapper? repo) (rake db:automigrate :type :setup)
         (schema-rb? repo) (rake db:schema:load :type :setup)
         (migrations? repo) (rake db:migrate :type :setup)
         :else nil)
        (when (rspec? repo)
          (rspec-test repo))
        (when (cucumber? repo)
          (rake cucumber :type :test))
        (when (test-unit? repo)
          (rake test :type :test))]
       (filter identity)))))

(defmethod inference/infer-actions* :rails [_ repo]
  (spec repo))

(defmethod inference/node* :rails [_]
  circle.backend.nodes.rails/rails-node)