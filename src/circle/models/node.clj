(ns circle.models.node
  (:use [circle.utils.args :only (require-args)])
  (:use [circle.utils.validation :only (defn-v)])
  (:use [circle.utils.model-validation :only (validate!)])
  (:use [circle.utils.model-validation-helpers :only (require-keys key-types)])
  (:require [somnium.congomongo :as mongo]))

(def node-validation [(require-keys [:name :ami :username :keypair-name :public-key :private-key])
                      (key-types {:name String})])

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

