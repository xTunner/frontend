(ns circle.backend.build.inference.mysql
  (:require [circle.sh :as sh])
  (:require [circle.backend.action.bash :as bash]))

(defn create-user
  "Returns a build action to create a new MySQL user"
  [{:keys [username password]}]
  (when (not= "root" username)
    (bash/bash (sh/q
                (~(format "echo \"create user '%s'@'localhost' identified by '%s'\"" username password) "|" mysql -u root)
                (~(format "echo \"grant all privileges on *.* to '%s'@'localhost'\"" username) "|" mysql -u root))
               :name (format "create mysql user %s" username)
               :type :setup)))

;; the path for the mysql socket on our AMI
(def ami-ubuntu-socket "/var/run/mysqld/mysqld.sock")

(defn ensure-socket
  "Makes sure the mysql socket is present, when the user expects it in a non-standard location. Storenvy is one example"
  [path]
  (when (not= path ami-ubuntu-socket)
    (bash/bash (sh/q
                (ln -s ~ami-ubuntu-socket ~path))
               :type :setup
               :name "Setup mysql socket")))