(ns circle.backend.build.inference.test-rails
  (:use midje.sweet)
  (:use circle.backend.build.inference.rails)
  (:require fs)
  (:require [circle.backend.git :as git]))

(def empty-repo "test/circle/backend/build/inference/test_dirs/empty")

(fact "bundler?"
  (let [bundler-repo "test/circle/backend/build/inference/test_dirs/bundler_1"]
    (bundler? bundler-repo) => true
    (bundler? empty-repo) => false))

(fact "bundler action"
  (let [bundler-repo "test/circle/backend/build/inference/test_dirs/bundler_1"]
    (map :name (spec bundler-repo)) => ["bundle install"]))

(fact "rspec? works"
  (let [rspec-repo "test/circle/backend/build/inference/test_dirs/rspec_1"]
    (rspec? rspec-repo) => true
    (rspec? empty-repo) => false))

(fact "rspec uses bundler when appropriate"
  (let [bundler-repo "test/circle/backend/build/inference/test_dirs/rspec_1"]
    (->> (spec bundler-repo) (map :name) (into #{})) => (contains #"bundle exec rspec spec"))
  (let [no-bundler-repo "test/circle/backend/build/inference/test_dirs/no_bundler_1"]
    (->> (spec no-bundler-repo) (map :name)) => ["rspec spec"]))

(fact "rspec action"
  (let [repo "test/circle/backend/build/inference/test_dirs/rspec_1"]
    (->> (spec repo) (map :name) (into #{})) => (contains "bundle exec rspec spec")))

(fact "rspec actions have type :test"
  (let [repo "test/circle/backend/build/inference/test_dirs/rspec_1"]
    (->> (spec repo) (map :type) (into #{})) => (contains :test)))

(fact "db:create when db.yml action"
  (let [repo "test/circle/backend/build/inference/test_dirs/database_yml_2/"]
    (database-yml? repo) => true
    (->> (spec repo) (map :name) (into #{}))  => (contains "bundle exec rake db:create:all --trace")))

(fact "inference finds database yaml"
  (let [repo "test/circle/backend/build/inference/test_dirs/database_yml_1/"]
    (database-yml? repo) => false
    (find-database-yml repo) => (fs/join repo "config/database.example.yml")))

(fact "data-mapper? works"
  (let [repo "test/circle/backend/build/inference/test_dirs/dm_rails_1/"]
    (data-mapper? repo) => true
    (data-mapper? empty-repo) => falsey))

(fact "call rake db:automigrate when using dm-rails"
  (let [repo "test/circle/backend/build/inference/test_dirs/dm_rails_1/"]
    (spec repo)
    (->> (spec repo) (map :name) (into #{})) => (contains #"rake db:automigrate")))

(fact "copy database.example.yml to database.yml action"
  ;; repo with database.example.yml
  (let [example-repo "test/circle/backend/build/inference/test_dirs/database_yml_1/"]
    (->> (spec example-repo) (map :name)) => ["copy database.yml" "rake db:create:all --trace"]))