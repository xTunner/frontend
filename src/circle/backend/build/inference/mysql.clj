(ns circle.backend.build.inference.mysql
  (:require [circle.sh :as sh])
  (:require [circle.backend.action.bash :as bash]))

(defn create-user
  "Returns a build action to create a new MySQL user"
  [{:keys [username password]}]
  (bash/bash (sh/q
              (~(format "echo \"create user '%s'@'localhost' identified by '%s'\"" username password) "|" mysql -u root)
              (~(format "echo \"grant all privileges on *.* to '%s'@'localhost'\"" username) "|" mysql -u root))
   :name (format "create mysql user %s" username)))

