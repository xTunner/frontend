(ns circle.model.node
  (:use [circle.util.validation :only (validate defn-v)])
  (:use [circle.util.model-validation :only (validate!)])
  (:use [circle.util.model-validation-helpers :only (require-keys key-types)])
  (:require [somnium.congomongo :as mongo]))

(def node-validation [(require-keys [:name :ami :username :keypair-name :public-key :private-key])
                      (key-types {:name String})])

(defmethod validate ::Node [tag obj]
  (validate! node-validation obj))

(defn node [& {:keys [name  ;; string
                      owner ;; project-id
                      ami
                      instance-type
                      security-groups
                      availability-zone
                      username
                      keypair-name
                      public-key
                      private-key]
               :as args}]
  (validate! node-validation args))

(defn-v insert! [^::Node node]
  (mongo/insert! :nodes node))

