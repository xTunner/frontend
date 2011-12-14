(ns circle.backend.build.template
  (:require [circle.backend.action.load-balancer :as lb])
  (:refer-clojure :exclude [find])
  (:use [circle.backend.action.nodes :only (start-nodes stop-nodes)])
  (:use [circle.backend.action.tag :only (tag-revision)])
  (:use [circle.backend.action.vcs :only (checkout)])
  (:require [circle.backend.action.rvm :as rvm]))

(def build-templates {:build {:prefix [start-nodes
                                       checkout
                                       rvm/trust]
                              :suffix [stop-nodes]}

                      :deploy {:prefix [start-nodes
                                        checkout
                                        rvm/trust]
                               :suffix [tag-revision
                                        lb/add-instances
                                        lb/wait-for-healthy
                                        lb/shutdown-remove-old-revisions]}

                      :staging {:prefix [start-nodes
                                         checkout
                                         rvm/trust]
                                :suffix [tag-revision]}
                      :empty {:prefix []
                              :suffix []}})

(defn find [name]
  (-> name keyword build-templates))