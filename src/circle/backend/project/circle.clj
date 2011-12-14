(ns circle.backend.project.circle
  (:require [circle.backend.build.config :as config]))

(defn circle-build []
  (config/build-from-name "Circle" :job-name :build))

(defn circle-deploy []
  (config/build-from-name "Circle" :job-name :deploy))

