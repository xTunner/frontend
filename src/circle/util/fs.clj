(ns circle.util.fs
  "utilities for working with files and directories"
  (:require fs))

(defn files-matching
  "Recursively traverse dir, Return filenames matching the regex"
  [dir re]
  (->> dir
       (fs/iterdir)
       (map (fn [[root dirs files]]
         (->> files
              (map (fn [filename]
                     (re-find re (fs/join root filename))))
              (remove empty?)
              (seq)
              (filter identity))))
       (filter seq)
       (apply concat)))

(defn re-file?
  "True if the contents of the file match the regex"
  [file re]
  (when (fs/exists? file)
    (boolean (seq (re-find re (slurp file))))))