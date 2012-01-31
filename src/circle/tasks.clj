(ns circle.tasks
  "Like rake tasks, for the repl"
  (:require [circle.backend.build.run :as run])
  (:require [circle.util.mongo :as mongo])
  (:require [circle.backend.build.config :as config]))

(defn one-click-deploy [& args]
  "Deploy immediately"
  (let [build (config/build-from-url
               "https://github.com/arohner/CircleCI"
               :job-name
               :deploy)]
    (mongo/ensure-object-id-ref :builds build)
    (apply run/run-build build args)))