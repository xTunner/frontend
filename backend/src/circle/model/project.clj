(ns circle.model.project
  (:use [circle.util.validation :only (validate defn-v)])
  (:use [circle.util.model-validation :only (validate!)])
  (:use [circle.util.model-validation-helpers :only (require-keys key-types)])
  (:require [somnium.congomongo :as mongo]))

(def project-validation [(require-keys [:name :vcs-type :vcs-url :aws-credentials :actions :node-id])])

(defmethod validate ::Project [tag obj]
  (validate! project-validation obj))

(defn project [& {:keys [name
                         vcs-type
                         vcs-url
                         aws-credentials
                         node-id
                         actions]
                  :as args}]
  (validate! project-validation args)
  args)

(defn-v insert! [^::Project p]
  (mongo/insert! :projects p))