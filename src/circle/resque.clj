(ns circle.resque
  (:require [circle.redis])
  (:use [circle.util.core :only (defn-once)])
  (:require [resque-clojure.core :as resque]))

(defn-once init
  (resque/configure (merge circle.redis/*current-db*
                           {:max-workers 20}))
  ;; (resque/start)
  )


;;(resque/enqueue "testqueue" "circle.airbrake/growl" "Airbrake" "Test" "Test")
