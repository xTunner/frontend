(ns circle.backend.action.tag
  (:use [circle.backend.action :only (defaction)])
  (:require [circle.backend.nodes :as nodes])
  (:require [circle.backend.ec2-tag :as tag]))

(defaction tag-revision []
  {:name "tag revision"}
  (fn [build]
    (tag/add-tags (-> @build :instance-ids)
                   {:rev (-> @build :vcs-revision)})))