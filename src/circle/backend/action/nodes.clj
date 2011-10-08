(ns circle.backend.action.nodes
  (:require [circle.backend.nodes :as nodes])
  (:use [circle.backend.action :only (defaction)]))

(defaction start-nodes []
  {:name "start nodes"}
  (fn [context]
    (nodes/converge {(-> context :build :group) (-> context :build :num-nodes)})
    {:success true}))

(defaction stop-nodes []
  {:name "stop nodes"}
  (fn [context]
    (nodes/converge {(-> context :build :group) 0})
    {:success true}))