(ns circle.backend.action.tag
  (:require [circle.model.build :as build])
  (:use [circle.backend.action :only (defaction)])
  (:use [circle.util.except :only (throw-if-not)])
  (:require [circle.backend.nodes :as nodes])
  (:require [circle.backend.ec2 :as ec2]))

(defaction tag-revision []
  {:name "tag revision"}
  (fn [build]
    (throw-if-not (-> @build :vcs_revision) "build must contain vcs revision")
    (ec2/add-tags (-> @build :instance-ids)
                  {:rev (-> @build :vcs_revision)
                   :build (build/build-name build)
                   :job-name (-> @build :job-name)})))