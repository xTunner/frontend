(ns circle.backend.action.tag
  (:use [circle.backend.action :only (defaction action-fn)])
  (:require [circle.backend.nodes :as nodes])
  (:require [circle.backend.ec2-tag :as tag]))

(defaction tag-revision []
  {:name "tag revision"}
  (fn [context]
    (tag/add-tags (nodes/group-instance-ids (-> context :build :group))
                  {:rev (-> context :build :vcs-revision)})
    {:success true}))