(ns circle.backend.build.inference.test-postgres
  (:use midje.sweet)
  (:require [circle.backend.build.inference.rails :as rails])
  (:require [circle.test-utils :as test])
  (:require fs)
  (:require [circle.backend.git :as git]))

(fact "postgres-role"
  (let [postgres-repo (test/test-repo "postgres_1")]
    (-> (rails/ensure-db-user postgres-repo) :command) => (contains #"psql -c")))
