(ns circle.backend.build.template
  (:require [circle.backend.action.load-balancer :as lb])
  (:refer-clojure :exclude [find])
  (:use [circle.backend.action.nodes :only (start-nodes stop-nodes)])
  (:use [circle.backend.action.tag :only (tag-revision)])
  (:use [circle.util.except :only (throw-if-not assert!)])
  (:use [circle.backend.action.vcs :only (checkout)])
  (:require [circle.backend.action.rvm :as rvm]))

(defn build-templates []
  ;;; defn, solely so this file doesn't need to be reloaded when reloading action fns.
  {:build {:prefix [start-nodes
                    checkout
                    rvm/trust
                    rvm/rvm-use]
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
  (get (build-templates) (keyword name)))

(defn find! [name]
  (assert! (find name) "could not find template %s" name))

(defn apply-template [template-name actions]
  (let [template (find! template-name)
        before (map #(apply % []) (-> template :prefix))
        after (map #(apply % []) (-> template :suffix))]
    (concat before actions after)))