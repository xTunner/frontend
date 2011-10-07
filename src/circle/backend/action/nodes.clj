(ns circle.backend.action.nodes
  (:require [circle.backend.nodes :as nodes])
  (:require [circle.backend.action :as action]))

(defn start-nodes []
  (action/action 
   :name (format "start nodes")
   :act-fn (fn [context]
             (def start-result (nodes/converge {(-> context :build :group) (-> context :build :num-nodes)}))
             {:success true})))

(defn stop-nodes []
  (action/action 
   :name (format "stop nodes")
   :act-fn (fn [context]
             (nodes/converge {(-> context :build :group) 0})
             {:success true})))