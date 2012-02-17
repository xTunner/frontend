(ns circle.backend.build.queue
  (:use [clojure.core.incubator :only (-?>)])
  (:require circle.redis)
  (:require [redis.core :as redis])
  (:require [resque-clojure.core :as resque])
  (:require [circle.backend.ec2 :as ec2])
  (:use [circle.util.except :only (throwf)])
  (:require [clj-time.core :as time])
  (:use [circle.util.args :only (require-args)])
  (:require [circle.model.build :as build]))

(def build-queue "builds")

(defn enqueue-build
  "Adds a build to the queue"
  [build]
  (dosync
   (alter build assoc :queued-at (-> (time/now) (.toDate))))
  (build/update-mongo build)
  (resque/enqueue build-queue "circle.backend.build.run/fetch-run-build" (-> @build :_id (str))))