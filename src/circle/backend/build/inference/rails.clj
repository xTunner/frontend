(ns circle.backend.build.inference.rails
  (:require [circle.backend.build :as build])
  (:require [circle.sh :as sh])
  (:require fs)
  (:use [clojure.tools.logging :only (errorf)])
  (:use [circle.backend.action.bash :only (bash)])
  (:use [circle.backend.action :only (defaction action)])
  (:require circle.backend.nodes.rails)
  (:require [circle.backend.build.inference :as inference]))

(defn bundler?
  "True if this project is using bundler"
  [repo]
  (fs/exists? (fs/join repo "Gemfile")))

(defn rspec?
  "True if this project has rspec tests that need to be run"
  [repo]
  (-> (fs/join repo "spec")
      (fs/listdir)
      (seq)
      (boolean)))

(defn test-unit? [repo]
  (-> (fs/join repo "test")
      (fs/listdir)
      (seq)
      (boolean)))

(defn find-example-yml
  "Look in repo/config/ for a file named database.example.yml or similar, and return the path, or nil"
  [repo]
  (let [yml (->> (fs/join repo "config")
                 (fs/listdir)
                 (filter #(re-find #"database.*yml" %))
                 (first))]
    (when yml
      (fs/join repo "config" yml))))

(defaction ensure-database-yml []
  {:name "ensuring database.yml exists and is in the proper location"}
  (fn [build]
    (let [repo (build/checkout-dir build)
          db-yml (fs/join repo "database.yml")
          example-yml (find-example-yml repo)]
      (if (and (not (fs/exists? db-yml)) example-yml)
        (fs/copy example-yml db-yml)
        (errorf "couldn't find database.yml for project, things probably aren't going to end well")))))

(defn rspec-test
  "action to run the rspec tests"
  [& {:keys [bundler?]}]
  (if bundler?
    (bash (sh/q (bundle exec rspec spec)))
    (bash (sh/q (rspec spec)))))

(defn rake-test
  "action to run rake test"
  [& {:keys [bundler?]}]
  (if bundler?
    (bash (sh/q (bundle exec rake test)))
    (bash (sh/q (rake test)))))

(defn bundle-install []
  (bash (sh/q (bundle install)) :environment {:RAILS_GROUP :test}
        :name "bundle install"))

(defn actions
  "Returns the set of actions necessary for this project"
  [repo]
  (let [use-bundler? (bundler? repo)]
    (->>
     [(when use-bundler?
        (bundle-install))
      (when (rspec? repo)
        (rspec-test :bundler? use-bundler?))
      (when (test-unit? repo)
        (rake-test :bundler? use-bundler?))]
     (filter identity))))

(defmethod inference/infer-actions* :rails [_ repo]
  (actions repo))

(defmethod inference/node* :rails [_]
  circle.backend.nodes.rails/rails-node)