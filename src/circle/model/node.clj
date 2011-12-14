(ns circle.model.node
  (:use [circle.util.validation :only (validate defn-v)])
  (:use [circle.util.model-validation :only (validate!)])
  (:use [circle.util.model-validation-helpers :only (require-keys key-types)])
  (:require [somnium.congomongo :as mongo]))

(def node-validation [(require-keys [:name :project-id :ami :username :keypair-name :public-key :private-key])])

(defmethod validate ::Node [tag obj]
  (validate! node-validation obj))

(defn node [& {:keys [name  ;; string
                      owner ;; project-id
                      ami
                      instance-type
                      availability-zone
                      keypair-name
                      public-key
                      private-key
                      username
                      security-groups]
               :as args}]
  (validate! node-validation args))

(defn-v insert! [^::Node node]
  (mongo/insert! :nodes node))

