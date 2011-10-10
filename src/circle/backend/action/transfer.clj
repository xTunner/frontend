(ns circle.backend.action.transfer
  (:import java.io.File)
  (:require [clojure.string :as str])
  (:require [clj-ssh.ssh :as ssh])
  (:require pallet.compute)
  (:require [circle.backend.action :as action])
  (:require [circle.backend.ssh :as circle-ssh])
  (:require [circle.backend.action.bash :as bash])
  (:use [circle.utils.core :only (printfln)])
  (:use circle.utils.except))

(defn get-file
  [context remote-path local-path]
  (circle-ssh/with-session (-> context :node) ssh-session
    (ssh/sftp ssh-session
              :get
              remote-path
              (-> local-path java.io.FileOutputStream.
                  java.io.BufferedOutputStream.))))

(defn path-exists? [path]
  (.exists (File. path)))

(defn directory? [path]
  (.isDirectory (File. path)))

(defn get-files
  "copies over the seq of remote paths calls f with one argument, an input stream"
  [context remote-paths f]
  (circle-ssh/with-session (-> context :node) ssh-session
    (doseq [remote-path remote-paths]
      (f (ssh/sftp ssh-session
                :get
                remote-path)))))

(defn write-files
  "copies over the seq of remote paths to local-dir"
  [context remote-paths local-dir & {:keys [no-overwrite]}]
  (let [local-dir (if (re-find #"/$" local-dir)
                    local-dir
                    (str local-dir "/"))]
    (circle-ssh/with-session (-> context :node) ssh-session
    (assert (and (path-exists? local-dir) (directory? local-dir)))
    (doseq [remote-path remote-paths
            :let [new-local (str local-dir (.getName (File. remote-path)))]]
      (when no-overwrite
        (throw-if (path-exists? new-local) "Attempting to transfer %s to %s, but file already exists" remote-path new-local))
      (printfln "%s -> %s" remote-path new-local)
      (ssh/sftp ssh-session
                :get
                remote-path)))))



(defn find-files
  "Runs find on the remote box, returns the list of files that matched. Find starts at directory, Pattern is a grep -P regex"
  [context directory pattern]
  (let [pattern (str pattern)] ;; in case we were passed a clojure
                               ;; regex, naively convert it. This
                               ;; should work most of the time
    (let [resp (bash/remote-bash context [(cd ~directory)
                                          (pipe
                                           (find .)
                                           (grep -P ~pattern))])]
      (if (action/successful? resp)
        (-> resp
            :out
            (clojure.string/split #"\n")
            (->>
             (map #(str (str/replace directory #"/$" "") "/" (str/replace % #"^./" "")))))))))