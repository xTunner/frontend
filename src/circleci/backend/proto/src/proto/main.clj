(ns proto.main
  (:require [clojure.contrib.io :as io])
  (:require [clj-yaml.core :as yaml])
  )

(defn init [& argv]
  (-> argv
      first
      io/reader
      slurp
      yaml/parse-string
      println))


(defn -main [& argv]
  (init *command-line-args*))
