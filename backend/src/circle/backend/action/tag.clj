(ns circle.backend.action.tag
  (:use [circle.backend.action :only (defaction)])
  (:use [circle.util.except :only (throw-if-not)])
  (:require [circle.backend.nodes :as nodes])
  (:require [circle.backend.ec2-tag :as tag]))

(defaction tag-revision []
  {:name "tag revision"}
  (fn [build]
    (throw-if-not (-> @build :vcs-revision) "build must contain vcs revision")
    (tag/add-tags (-> @build :instance-ids)
                  {:rev (-> @build :vcs-revision)})))