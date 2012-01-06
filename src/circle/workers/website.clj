(ns circle.workers.website
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.build.config :as config]))

(defn run-build-from-jruby [url]
  (let [build (config/build-from-url url)]
    (run/run-build build)))
