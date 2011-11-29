(ns circle.backend.action.tag
  (:use [circle.backend.action :only (defaction)])
  (:use [circle.util.except :only (throw-if-not)])
  (:require [circle.backend.nodes :as nodes])
  (:require [circle.backend.ec2 :as ec2]))

(defaction tag-revision []
  {:name "tag revision"}
  (fn [build]
    (throw-if-not (-> @build :vcs-revision) "build must contain vcs revision")
    (ec2/add-tags (-> @build :instance-ids)
                  {:rev (-> @build :vcs-revision)})))