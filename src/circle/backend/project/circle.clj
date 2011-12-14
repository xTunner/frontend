(ns circle.backend.project.circle
  (:require [circle.backend.build.config :as config]))

(defn circle-build []
  (config/build-from-name "CircleCI" :job-name :build))

(defn circle-deploy []
  (config/build-from-name "CircleCI" :job-name :deploy))

(defn circle-staging []
  (config/build-from-name "CircleCI" :job-name :staging))