(ns circle.backend.build.deploy
  "Deploying the circle app"
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.build.config :as config]))

(defn one-click-deploy [& args]
  "Deploy immediately"
  (let [build (config/build-from-url
               "https://github.com/arohner/CircleCI"
               :job-name
               :deploy)]
    (apply run/run-build build args)))