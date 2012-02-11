(ns circle.workers.website
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.build.config :as config]))

(defn run-build-from-jruby
  "Called from the website.

  infer - boolean
  why - a short string, such as 'edit', or 'trigger'
  who - a string, the id of the user that triggered the build"
  [url infer why who]

  ;; Run even when the build is disabled.
  (let [build (config/build-from-url url :infer infer :who who :why why)]
    (run/run-build build)))
