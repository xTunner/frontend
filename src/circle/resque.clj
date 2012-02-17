(ns circle.resque
  (:require [circle.env])
  (:use [circle.util.core :only (defn-once)])
  (:require [resque-clojure.core :as resque]))

(def uris {:development {:host "barracuda.redistogo.com"
                         :port 9477
                         :username "circle"
                         :password "45a7c5aeab1ae5ecf79b95b898232d6c"}
           :test {:host "carp.redistogo.com"
                  :port 9334
                  :username "circle"
                  :password "6c67063efb4a63915cf499d4cbc7d12e"}})

(defn-once init
  (resque/configure (merge (get uris (circle.env/env))
                           {:max-workers 10}))
  (resque/start ["builds"]))
