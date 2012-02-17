(ns circle.backend.build.test-queue
  (:use circle.backend.build.queue)
  (:use circle.backend.build.run)
  (:use circle.backend.build.test-utils)
  (:require [circle.model.build :as build])
  (:use midje.sweet)
  (:use [circle.util.retry :only (wait-for)]))

(fact "queuing builds works"
  (let [builds (take 10 (repeatedly #(minimal-build)))]
    (doall (map enqueue-build builds)) => anything
    (wait-for
     {:sleep 1000
      :tries 30}
     (fn []
       (->> builds
            (map #(build/fetch-build (-> @% :_id)))
            (every? build/successful?)))) => true))
