(ns circle.hooks
  (:require [org.danlarkin.json :as json])
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.project.circle :as circle])
  (:use [circle.backend.github-url :only (->ssh)])
  (:use [clojure.tools.logging :only (infof)]))

(defn process-json [github-json]
  (when (= "CircleCI" (-> github-json :repository :name))
    (let [build (circle/circle-build)]
      (dosync
       (alter build merge
              {:vcs-url (->ssh (-> github-json :repository :url))
               :repository (-> github-json :repository)
               :commits (-> github-json :commits)
               :vcs-revision (-> github-json :commits last :id)
               :num-nodes 1}))
      (infof "process-json: build: %s" @build)
      (run/run-build build))))

(defn github
  [url after ref json-string]
  (future (-> json-string json/decode process-json)))