(ns circle.backend.build.inference.test-mysql
  (:use midje.sweet)
  (:use circle.backend.build.inference.rails)
  (:use circle.backend.build.inference.test-rails)
  (:require fs)
  (:require [circle.backend.git :as git]))

(fact "mysql-user"
  (let [mysql-repo (test-repo "mysql_1")]
    (-> (ensure-db-user mysql-repo) :command) => (contains #"mysql -u")))