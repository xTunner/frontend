(ns circle.util.fs
  "utilities for working with files and directories"
  (:require fs)
  (:require [clojure.java.io :as io])
  (:import org.apache.commons.io.FileUtils)
  (:import (org.apache.commons.io.filefilter
            IOFileFilter
            TrueFileFilter))
  (:require [circle.sh :as sh]))

(def symlink-filter (reify IOFileFilter
                      (accept [this file]
                        (not (FileUtils/isSymlink file)))))

(defn all-files
  "Returns a list of all files in the repos"
  [dir]
  (when (fs/exists? dir)
    (map str (FileUtils/listFiles (io/as-file dir)
                                  symlink-filter
                                  symlink-filter))))

(defn files-matching
  "Returns a list of files that match re."
  [dir re]
  (->> dir
       (all-files)
       (filter #(re-find re %))))

(defn re-file?
  "True if the contents of the file match the regex"
  [file re]
  (when (fs/exists? file)
    (boolean (seq (re-find re (slurp file))))))

(defn line-count
  "returns the number of lines in the file"
  [file]
  (if (fs/exists? file)
    (->> file (slurp) (re-seq #"\n") (count))
    0))