(ns circle.backend.build.template
  (:require [circle.backend.action.load-balancer :as lb])
  (:refer-clojure :exclude [find])
  (:use [circle.backend.action.nodes :only (start-nodes stop-nodes)])
  (:require [circle.backend.action.tag :as tag])
  (:use [circle.util.except :only (throw-if-not assert!)])
  (:use [circle.backend.action.vcs :only (checkout)])
  (:require [circle.backend.action.rvm :as rvm]))

(defn build-templates []
  ;;; defn, solely so this file doesn't need to be reloaded when reloading action fns.
  {:build {:prefix [start-nodes
                    tag/tag-revision
                    checkout
                    rvm/rvm-use]
           :suffix [stop-nodes]}

   :deploy {:prefix [start-nodes
                     tag/tag-revision
                     checkout
                     rvm/trust]
            :suffix [lb/add-instances
                     lb/wait-for-healthy
                     lb/shutdown-remove-old-revisions]}

   :staging {:prefix [start-nodes
                      tag/tag-revision
                      checkout]
             :suffix []}

   :empty {:prefix []
           :suffix []}})

(defn find [name]
  (get (build-templates) (keyword name)))

(defn find! [name]
  (assert! (find name) "could not find template %s" name))

(defn apply-template [template-name actions]
  (let [template (find! template-name)
        before (map #(apply % []) (-> template :prefix))
        after (map #(apply % []) (-> template :suffix))]
    (concat before actions after)))