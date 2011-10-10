(ns proto.main
  (:require proto.repl)
  (:require [clojure.contrib.io :as io])
  (:require [clojure.contrib.generic.functor :as functor])
  (:require [clj-yaml.core :as yaml])
  (:require [clojure.contrib.shell :as shell])
  (:import (java.io FileInputStream BufferedInputStream))
  (:import (org.apache.commons.compress.compressors.bzip2 BZip2CompressorInputStream))
  (:import org.xeustechnologies.jtar.TarInputStream)
  (:require [clojure.string :as string])
  (:require [clojure.contrib.seq-utils :as seq-utils])
  (:require [fs]))

(defn strip-dots
  "Remove all dots from a string"
  [s]
  (string/replace s "." ""))

(defn env-paths
  []
  (string/split (System/getenv "PATH") #":"))


(defn autoconf
  "Figure out the executable name for autoconf with the given version"
  [version]
  (let [mac-suffix (strip-dots version)
        ubuntu-suffix (str "-" version)
        suffixes [mac-suffix ubuntu-suffix]
        exe-names (map #(str "autoconf" %) suffixes)]
    (seq-utils/find-first fs/executable?
                          (for [name exe-names path (env-paths)]
                            (fs/join path name)))))

(defn shell-out [& args]
  (if (not= (:exit (apply shell/sh :return-map true args)) 0)
    (throw (Exception. (str "Failed thing: " args)))))

(defn autotools-handler
  [{type :type
    srcdir :srcdir
    configurations :configurations
    {autoconf-version :version} :autoconf}]
  (when (= type "autotools")
    (let [autoconf-version (or autoconf-version "")
          configurations (or configurations [])
          srcdir (or srcdir "")
          automake "automake"]
      (do
        (shell-out (autoconf autoconf-version) :dir srcdir)
        (shell-out "./configure" :dir srcdir)
        (shell-out "make" :dir srcdir)
        (shell-out "make" "check" :dir srcdir)))))


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
                      TarInputStream.)]
    (first (string/split (-> tis
                             .getNextEntry
                             .getName)
                         #"/"))))


(defn repo-handler
  [{repo :repo
    subdir :subdir :or ""}]
  (let [dir "scratch/"
        tar-file (str dir "tip.tar.bz2")]
                                        ; for now, assume it's mercurial
    (do
      (download (str repo "/archive/tip.tar.bz2") tar-file)
      (untar tar-file dir)
      {:srcdir (fs/join dir (tar-get-directory tar-file) subdir)})))


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
