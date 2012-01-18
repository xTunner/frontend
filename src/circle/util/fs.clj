(ns circle.util.fs
  "utilities for working with files and directories"
  (:require fs)
  (:require [clojure.string :as str])
  (:use [arohner.utils :only (inspect)])
  (:require [circle.sh :as sh]))

(defn files-matching
  "Returns a list of files that match glob. Glob is interpreted by `find` on the cmd line."
  [dir glob]
  (let [out (-> (circle.sh/sh (sh/q (find ~dir -name ~glob))) :out)]
    (when (seq out)
      (str/split out #"\n"))))

(defn re-file?
  "True if the contents of the file match the regex"
  [file re]
  (when (fs/exists? file)
    (boolean (seq (re-find re (slurp file))))))