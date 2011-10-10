(ns proto.main
  (:require proto.repl)
  (:require [clojure.contrib.io :as io])
  (:require [clojure.contrib.generic.functor :as functor])
  (:require [clj-yaml.core :as yaml])
  (:require [clojure.contrib.shell :as shell])
  (:import (java.io FileInputStream BufferedInputStream))
  (:import (org.apache.commons.compress.compressors.bzip2 BZip2CompressorInputStream))
  (:import org.xeustechnologies.jtar.TarInputStream)
  (:require [clojure.string :as string]))


(defn autotools-handler
  [{type :type
    subdir :subdir
    configurations :configurations
    autoconf-version :autoconf-version}]
  (println "do autotools")
  (when (= type "autotools") (println "autotools")))

(defn shell-out [& args]
  (if (not= (:exit (apply shell/sh :return-map true args)) 0)
    (throw (Exception. (str "Failed thing: " args)))))

(defn download
  "Download a file, and save it to a known filename"
  [remote-file local-file]
 ; (shell-out "curl" remote-file "-o" local-file "-s")
  (shell-out "cp" "scratch/saved.tar.bz2" local-file))

(defn untar
  "Untar a file, into directory"
  [tar-file directory]
;  (shell-out "tar" "jxf" tar-file "-C" directory)
  )

(defn tar-get-directory
  "Find the name of the directory that wraps the tar"
  [filename]
  (with-open [tis (-> filename
                      FileInputStream.
                      BufferedInputStream.
                      BZip2CompressorInputStream.
                      org.xeustechnologies.jtar.TarInputStream.)]
    (first (string/split (-> tis
                             .getNextEntry
                             .getName)
                         #"/"))))


(defn repo-handler
  [{repo :repo
    subdir :subdir}]
  (let [dir "scratch/"
        tar-file (str dir "tip.tar.bz2")]
                                        ; for now, assume it's mercurial
    (do
      (download (str repo "/archive/tip.tar.bz2") tar-file)
      (untar tar-file dir)
      {:srcdir (tar-get-directory tar-file)})))


(def handlers [repo-handler autotools-handler])


(defn run-handlers
  "On a configuration, run each of the handlers in turn, updating the config with the result of previous handlers"
  [config]
  (loop [c config hs handlers]
    (do
      (println c)
      (when hs
        (recur (into c
                     (or
                       ((first hs) c) {}))
                       (next hs))))))


(defn process-configurations
  "For each project definition, run all the handlers on it"
  [configurations]
  (map run-handlers (vals configurations)))



(defn init [& argv]
  (proto.repl/init)
  (def configuration (-> argv
                          first
                          io/reader
                          slurp
                          yaml/parse-string))
  (process-configurations configuration))


(defn -main []
  (init *command-line-args*))
