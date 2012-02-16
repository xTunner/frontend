(ns circle.tasks
  "Like rake tasks, for the repl"
  (:require [circle.backend.build.run :as run])
  (:require [circle.util.mongo :as mongo])
  (:require [clojure.pprint :as pprint])
  (:require [circle.backend.build.config :as config]))

(defn one-click-deploy [& args]
  "Deploy immediately"
  (let [build (config/build-from-url
               "https://github.com/circleci/circle"
               :job-name
               :deploy)]
    (mongo/ensure-object-id-ref :builds build)
    (let [result (apply run/run-build build args)]
      (pprint/pprint result)
      (println
       (if (-> @result :failed)
         "Failed"
         "Success")))))