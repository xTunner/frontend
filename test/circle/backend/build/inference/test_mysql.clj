(ns circle.backend.build.inference.test-mysql
  (:use midje.sweet)
  (:use circle.backend.build.inference.rails)
  (:use circle.backend.build.inference.test-rails)
  (:require fs)
  (:require [circle.backend.git :as git]))

(fact "mysql-user"
  (let [repo (test-repo "mysql_1")]
    (-> (ensure-db-user repo) :command) => (contains #"mysql -u")
    (need-mysql-socket? repo) => true
    (need-mysql-socket? empty-repo) => false))

(fact "mysql2 user"
  (let [repo (test-repo "mysql_2")]
    (-> (ensure-db-user repo) :command) => (contains #"mysql -u")
    (need-mysql-socket? repo) => true))
