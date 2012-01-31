(ns circle.backend.build.inference.test-postgres
  (:use midje.sweet)
  (:use circle.backend.build.inference.rails)
  (:use circle.backend.build.inference.test-rails)
  (:require fs)
  (:require [circle.backend.git :as git]))

(fact "postgres-role"
  (let [postgres-repo (test-repo "postgres_1")]
    (-> (ensure-db-user postgres-repo) :command) => (contains #"psql -c")))
