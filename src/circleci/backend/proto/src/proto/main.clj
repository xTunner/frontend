(ns proto.main
  (:require [clojure.contrib.io :as io])
  (:require [clojure.contrib.generic.functor :as functor])
  (:require [clj-yaml.core :as yaml])
  (:require [clojure.contrib.shell :as shell]))

(def handlers [repo-handler autotools-handler])


(defn run-handlers
  "Run all the handlers on a definition"
  [config]
  (for [h handlers] (h config)))


(defn process-handlers
  "For each project definition, run all the handlers on it"
  [configurations]
  (functor/fmap run-handlers configurations))


(defn autotools-handler
  [{type :type
    subdir :subdir
    configurations :configurations
    autoconf-version :autoconf-version}]
  (println "autotools handler")
  (when (= type "autotools") (println "autotools")))

(defn shell-out [& args]
  (if (not= (:exit (apply shell/sh :return-map true args)) 0)
    (throw (Exception. (str "Failed thing: " args)))))

(defn download
  "Download a file, and save it to a known filename"
  [remote-file local-file]
  (shell-out "curl" remote-file "-o" local-file "-s"))

(defn untar
  "Untar a file, into directory"
  [tar-file directory]
  (shell-out "tar" "jxf" tar-file "-C" directory))

(defn tar-get-directory
  "Find the name of the directory that wraps the tar"
  [tar-file]
  (shell-out "tar" "tf" tar-file "|" "head" "-n" "1" "|" "sed" "'s/\\.*$//'"))


(defn repo-handler
  [{repo :repo
    subdir :subdir}]
  (let [dir "scratch/"
        tar-file (str dir "tip.tar.bz2")]
                                        ; for now, assume it's mercurial
    (do
      (download (str repo "/archive/tip.tar.bz2") tar-file)
      (untar tar-file dir)
      (tar-get-directory tar-file))))


(defn init [& argv]
  (-> argv
      first
      io/reader
      slurp
      yaml/parse-string
      process-handlers
      println))


(defn -main []
  (init *command-line-args*))
