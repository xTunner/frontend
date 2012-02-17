(ns circle.backend.build.inference.test-mysql
  (:use midje.sweet)
  (:require [circle.test-utils :as test])
  (:require [circle.backend.build.inference.rails :as rails])
  (:require fs)
  (:require [circle.backend.git :as git]))

(fact "mysql-user"
  (let [repo (test/test-repo "mysql_1")]
    (-> (rails/ensure-db-user repo) :command) => (contains #"mysql -u")
    (rails/need-mysql-socket? repo) => true
    (rails/need-mysql-socket? test/empty-repo) => false))

(fact "mysql2 user"
  (let [repo (test/test-repo "mysql_2")]
    (-> (rails/ensure-db-user repo) :command) => (contains #"mysql -u")
    (rails/need-mysql-socket? repo) => true))
