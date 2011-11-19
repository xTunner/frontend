(ns circle.backend.build.template
  (:require [circle.backend.action.load-balancer :as lb])
  (:use [circle.backend.action.nodes :only (start-nodes stop-nodes)])
  (:use [circle.backend.action.tag :only (tag-revision)])
  (:use [circle.backend.action.vcs :only (checkout)]))

(def build-templates {:build {:prefix [start-nodes checkout]
                              :suffix [stop-nodes]}
                      
                      :deploy {:prefix [start-nodes checkout]
                               :suffix [tag-revision
                                        lb/add-instances
                                        lb/wait-for-healthy
                                        lb/shutdown-remove-old-revisions]}})

(defn find [name]
  (-> name keyword build-templates))