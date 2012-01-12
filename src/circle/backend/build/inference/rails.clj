(ns circle.backend.build.inference.rails
  (:require [circle.backend.build :as build])
  (:require [circle.sh :as sh])
  (:require fs)
  (:use [arohner.utils :only (inspect)])
  (:use [clojure.tools.logging :only (errorf)])
  (:use [circle.backend.action.bash :only (bash)])
  (:use [circle.backend.action :only (defaction action)])
  (:require [circle.util.map :as map])
  (:require circle.backend.nodes.rails)
  (:require [circle.backend.build.inference :as inference]))

(defn bundler?
  "True if this project is using bundler"
  [repo]
  (fs/exists? (fs/join repo "Gemfile")))

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

(defn find-database-yml
  "Look in repo/config/ for a file named database.example.yml or similar, and return the path, or nil"
  [repo]
  (let [yml (->> (fs/join repo "config")
                 (fs/listdir)
                 (filter #(and (re-find #"database.*yml" %) (re-find #"example" %)))
                 (first))]
    (when yml
      (fs/join repo "config" yml))))

(defaction ensure-database-yml []
  {:name "ensuring database.yml exists and is in the proper location"}
  (fn [build]
    (let [repo (build/checkout-dir build)
          db-yml (fs/join repo "database.yml")
          example-yml (find-database-yml repo)]
      (if (and (not (fs/exists? db-yml)) example-yml)
        (fs/copy example-yml db-yml)
        (errorf "couldn't find database.yml for project, things probably aren't going to end well")))))

(defn rspec-test
  "action to run the rspec tests"
  [& {:keys [bundler?]}]
  (if bundler?
    (bash (sh/q1 (bundle exec rspec spec)))
    (bash (sh/q1 (rspec spec)))))

(defn rake-test
  "action to run rake test"
  [& {:keys [bundler?]}]
  (if bundler?
`    (bash (sh/q (bundle exec rake test)))
    (bash (sh/q (rake test)))))

(defn bundle-install []
  (bash (sh/q (bundle install)) :environment {:RAILS_GROUP :test}
        :name "bundle install"))

(defmacro cmd [body & {:keys [environment]}]
  `(bash (sh/q ~@body) :environment ~environment))

(defmacro rake [& body]
  `(cmd (~'rake ~@body --trace) :environment {:RAILS_ENV :test}))

(defn spec
  "Returns the set of actions necessary for this project"
  [repo]
  (let [use-bundler? (bundler? repo)
        found-db-yml (find-database-yml repo)
        need-cp-db-yml? (and (not (database-yml? repo))
                             found-db-yml)
        has-db-yml? (or (database-yml? repo) (find-database-yml repo))]
    (->>
     {:setup [(when use-bundler?
                (bundle-install))
              (when need-cp-db-yml?
                (bash (sh/q1 (cp ~found-db-yml "config/database.yml")) :name "copy database.yml"))
              (when has-db-yml?
                (rake db:create))
              (cond
               (schema-rb? repo) (rake db:schema:load)
               (migrations? repo) (rake db:migrate)
               :else nil)]
      :test [(when (rspec? repo)
               (rspec-test :bundler? use-bundler?))
             (when (test-unit? repo)
               (rake-test :bundler? use-bundler?))]}
     (map/map-vals (fn [actions]
                     (filter identity actions)))
     (map/filter-vals (fn [actions] (-> actions (seq) (boolean)))))))

(defmethod inference/infer-actions* :rails [_ repo]
  (spec repo))

(defmethod inference/node* :rails [_]
  circle.backend.nodes.rails/rails-node)