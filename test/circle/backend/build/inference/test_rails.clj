(ns circle.backend.build.inference.test-rails
  (:use midje.sweet)
  (:use circle.backend.build.inference.rails)
  (:require [circle.backend.build.test-utils :as test])
  (:require fs)
  (:require [circle.backend.git :as git]))

(fact "bundler?"
  (let [repo (test/test-repo "bundler_1")]
    (bundler? repo) => true
    (bundler? test/empty-repo) => false))

(fact "bundler action"
  (let [repo (test/test-repo "bundler_1")]
    (->> (spec repo) (map :name)) => (contains ["bundle install"])))

(fact "blacklist when Gemfile present"
  (let [repo (test/test-repo "bundler_1")]
    (->> (spec repo) (map :name)) => (contains ["blacklist problematic gems"])))

(fact "rspec? works"
  (let [repo (test/test-repo "rspec_1")]
    (rspec? repo) => true
    (rspec? test/empty-repo) => false))

(fact "rspec? doesn't trigger with no .rb files"
  (let [repo (test/test-repo "rspec_empty")]
    (rspec? repo) => false))

(fact "test-unit? doesn't trigger with no .rb files"
  (let [repo (test/test-repo "rake_test_empty")]
    (test-unit? repo) => false))

(fact "rspec uses bundler when appropriate"
  (let [repo (test/test-repo "rspec_1")]
    (->>(spec repo) (map :name) (into #{})) => (contains #"bundle exec rspec spec"))
  (let [no-bundler-repo (test/test-repo "no_bundler_1")]
    (->> (spec no-bundler-repo) (map :name)) => (contains "rspec spec")))

(fact "rspec action"
  (let [repo (test/test-repo "rspec_1")]
    (->> (spec repo) (map :name) (into #{})) => (contains "bundle exec rspec spec")))

(fact "rspec actions have type :test"
  (let [repo (test/test-repo "rspec_1")]
    (->> (spec repo) (map :type) (into #{})) => (contains :test)))

(fact "data-mapper? works"
  (let [repo (test/test-repo "dm_rails_1")]
    (data-mapper? repo) => true
    (data-mapper? test/empty-repo) => falsey))

(fact "Call rake db:create and db:migrate when using activerecord"
  (let [repo (test/test-repo "database_yml_2")
        action-names (->> (spec repo) (map :name))]
    (database-yml? repo) => true
    action-names => (contains [(contains "bundle exec rake db:create --trace")])))

(fact "call rake db:create and db:automigrate when using dm-rails"
  (let [repo (test/test-repo "dm_rails_1")]
    (spec repo)
    (->> (spec repo) (map :name)) => (contains [(contains #"rake db:create --trace")
                                                (contains #"rake db:automigrate")])))

(fact "jasmine triggers"
  (let [repo (test/test-repo "jasmine")]
    (jasmine? repo) => true
    (->> (spec repo) (map :name)) => (contains "bundle exec rake jasmine:ci --trace")))

(fact "Generate database.yml when not present"
  ;; repo with database.example.yml
  (let [example-repo (test/test-repo "database_yml_1")]
    (->> (spec example-repo) (map :name)) => (contains [(contains "Generate database.yml")
                                                        (contains "rake db:create --trace")] :gaps-ok)))

(fact "rvm trust is called when the repo contains a .rvmrc"
  (->> (spec test/empty-repo) (map :name)) =deny=> (contains "rvm rvmrc trust")
  (->> (spec (test/test-repo "rvmrc")) (map :name)) => (contains "rvm rvmrc trust"))

(fact "migrate? returns true when there are files in db/migrate"
  (->> (spec (test/test-repo "rails_migrations")) (map :name)) => (contains #"rake db:migrate"))

(fact "generate-db-yml contains an adapter"
  (generate-database-yml-str (test/test-repo "database_yml_1")) => (contains "adapter: postgresql")
  (generate-database-yml-str (test/test-repo "dm_rails_1")) => (contains "adapter: mysql"))