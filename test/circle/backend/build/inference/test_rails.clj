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

(fact "rspec action"
  (let [repo "test/circle/backend/build/inference/test_dirs/rspec_1"]
    (map :name (spec repo)) => ["rspec spec"]))

(fact "db:create when db.yml action"
  (let [repo "test/circle/backend/build/inference/test_dirs/database_yml_2/"]
    (database-yml? repo) => true
    (->> (spec repo) :setup (map :name))  => ["rake db:create"]))

(fact "inference finds database yaml"
  (let [repo "test/circle/backend/build/inference/test_dirs/database_yml_1/"]
    (database-yml? repo) => false
    (find-database-yml repo) => (fs/join repo "config/database.example.yml")))

(fact "copy database.example.yml to database.yml action"
  ;; repo with database.example.yml
  (let [example-repo "test/circle/backend/build/inference/test_dirs/database_yml_1/"]
    (->> (spec example-repo) :setup (map :name)) => ["copy database.yml" "rake db:create"]))