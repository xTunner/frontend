(ns circle.workers.website
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.build.config :as config]))

(defn run-build-from-jruby [url build-id]
  (let [build (config/build-from-url url)]
    (run/run-build build :id build-id)))
