(ns circle.resque
  (:require [circle.redis])
  (:use [circle.util.core :only (defn-once)])
  (:require circle.redis)
  (:require [resque-clojure.core :as resque]))

(defn-once init
  (circle.redis/init)
  (resque/configure (merge circle.redis/*current-db*
                           {:max-workers 5}))
  (resque/start ["builds"]))


;;(resque/enqueue "testqueue" "circle.airbrake/growl" "Airbrake" "Test" "Test")
