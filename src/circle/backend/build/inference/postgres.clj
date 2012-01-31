(ns circle.backend.build.inference.postgres
  (:require [circle.sh :as sh])
  (:require [circle.backend.action.bash :as bash]))

(defn create-role
  "Returns a build action to create a new postgres user"
  [{:keys [username password]}]
  (bash/bash (sh/q
              (psql -c ~(format "'create role \"%s\" login createdb'" username)))
             :name (format "create postgres role %s" username)
             :type :setup))