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

(defn bundle-fn
  "Wraps a one-line stevedore script in 'bundle exec', if the project
  is using bundler. Returns new stevedore, or unmodified stevedore."
  [build steve]
  (if (-> @build :bundler?)
    (concat '[bundle exec] (first steve))
    steve))

(defn rspec-test
  "action to run the rspec tests"
  []
  (action :name "rspec tests"
          :act-fn (fn [build]
                    (bash (bundle-fn build (sh/q (rspec spec)))))))

(defn rake-test
  "action to run rake test"
  []
  (action :name "rake tests"
          :act-fn (fn [build]
                    (bash (bundle-fn build (sh/q (rake test)))))))

(defn bundle-install []
  (action :name "bundle install"
          :act-fn (fn [build]
                    (bash (sh/q (bundle exec))))))

(defn actions
  "Returns the set of actions necessary for this project"
  [repo]
  (->>
   [(when (bundler? repo)
      (bundle-install))
    (if (rspec? repo)
      (rspec-test)
      (rake-test))]
   (filter identity)))

(defmethod inference/infer-actions* :rails [_ repo]
  (actions repo))

(defmethod inference/node* :rails [_]
  circle.backend.nodes.rails/rails-node)