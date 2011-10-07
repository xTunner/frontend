(ns proto.main
  (:require [clojure.contrib.io :as io])
  (:require [clojure.contrib.generic.functor :as functor])
  (:require [clj-yaml.core :as yaml]))

(declare autotools-handler)

(def handlers '(autotools-handler))


(defn run-handlers
  "Run all the handlers on a definition"
  [config]
  (map #(% config) handlers))


(defn process-handlers
  "For each project definition, run all the handlers on it"
  [configurations]
  (functor/fmap run-handlers configurations))


(defn autotools-handler
  [{type :type
    subdir :subdir
    configurations :configurations
    autoconf-version :autoconf-version}]
  (when (= type "autotools") "print autotools"))



(defn init [& argv]
  (-> argv
      first
      io/reader
      slurp
      yaml/parse-string
      process-handlers))


(defn -main [& argv]
  (init *command-line-args*))
