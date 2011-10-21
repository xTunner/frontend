(ns circle.proto
  (:require circle.repl)
  (:require [clojure.contrib.io :as io])
  (:require [clojure.contrib.generic.functor :as functor])
  (:require [clj-yaml.core :as yaml])
  (:require [clojure.contrib.shell :as shell])
  (:import (java.io FileInputStream BufferedInputStream))
  (:import (org.apache.commons.compress.compressors.bzip2 BZip2CompressorInputStream))
  (:import org.xeustechnologies.jtar.TarInputStream)
  (:require [clojure.string :as string])
  (:require [clojure.contrib.seq-utils :as seq-utils])
  (:use circle.utils.except)
  (:require [clj-time.core :as time])
  (:use [circle.db :only (with-conn)])
  ;; (:require [circle.model.shell-command :as model])
  (:require [fs]))

(defn strip-dots
  "Remove all dots from a string"
  [s]
  (string/replace s "." ""))

(defn env-paths
  []
  (string/split (System/getenv "PATH") #":"))


;;;  How can we quickly get a clone that we can experiment on? A full
;;;  clone includes full history, file data, and remote branches. In
;;;  the best case, we want no history, no remote branches, and
;;;  possibly even no file data (ie, just directory listings).
  

;;; SVN
;;;   - Bare checkout, no history
;;;     $ svn export
;;; 
;;; 
;;; Git:
;;;   - Full clone, using git protocol (41s)
;;;     $ git clone git@github.com:coffeemug/rethinkdb.git
;;;     - 41s
;;; 
;;;   - Full clone, using http protocol (50s ish)
;;;     $ git clone https://github.com/coffeemug/rethinkdb/
;;;      
;;;   - Shallow clone using remote git archive
;;;     $ git archive --remote=url url
;;;     - Not supported on all git hosts, including github
;;; 
;;;   - Shallow git clone with depth (17s)
;;;     $ git clone --depth=1 git@github.co:coffeemug/rethinkdb.git
;;;   
;;;   - Download a tarball directly
;;;     $ wget https://nodeload.github.com/coffeemug/rethinkdb/tarball/master.tar.gz
;;;     - awkward, since you need to authenticate first using cookies
;;;     - github only
;;; 
;;;   - Fetch only one branch, shallowly (8s)
;;;     $ mkdir rethinkdn
;;;     $ cd rethinkdb
;;;     $ git init
;;;     $ git remote add origin git@github.com:pbiggar/rethinkdb.git
;;;     $ git fetch origin HEAD
;;;     $ git pull origin HEAD
;;;     - this will work pretty much anywhere
;;; 
;;;   - Might be faster still, see super-advanced possibilities:
;;;     - http://progit.org/book/ch9-6.html
;;;     - http://stackoverflow.com/questions/1178389/
;;; 
;;; hg:
;;;   - wget http://hg.mozilla.org/mozilla-central/archives/tip.tar.bz2
;;;     Tarball with no history


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

(defn record-program-start
  "Record that a program has started, with pertinent information"
  [args env timestamp]
  ;; (model/insert {:args args :env env :starting-time timestamp})
  )

(defn record-program-end
  "Records that a program has ended, with the result of that program"
  [program-id out err exit timestamp]
  ;; (model/update {:id program-id :out out :err err :exit exit :ending-time timestamp})
  )

(defn fetch-keyword-argument
  "If the given keyword is in the argument list, return the value that follows it, else nil"
  [values keyword]
  (second (drop-while #(not= % keyword) values)))

(defn shell-out [& args]
  (with-conn
    (let [passed-env (fetch-keyword-argument args :env)
          env (apply shell/sh "env" passed-env)
          program-id (record-program-start args env (time/now))
          {:keys [out err exit]} (apply shell/sh :return-map true args)
          _ (record-program-end program-id out err exit (time/now))]
      (throw-if-not (and (= exit 0) (= err ""))
                    (throwf "Failed (%d): %s\n%s\n%s" exit args out err)))))

; there is a program called autogen.sh on sourceforge that apparently
; does magic here
(defn autotools-generate
  [srcdir]
  (let [exists? #(fs/exists? (fs/join srcdir %))
        has-configure-in (some exists? ["configure.in" "configure.ac"])
        has-makefile-am (exists? "Makefile.am")
        has-configure (exists? "configure")
        has-makefile (exists? "Makefile")
        has-autogen-sh (exists? "autogen.sh")]
    (if-not (or has-makefile has-configure)
      (if has-autogen-sh
        (shell-out "autogen.sh" :dir srcdir)
        (do
          (when has-makefile-am (shell-out "aclocal" :dir srcdir))
          (when has-configure-in (shell-out "autoconf"))
          (when has-makefile-am (shell-out "automake")))))))


(defn autotools-handler
  [{:keys [type srcdir configurations]
    {autoconf-version :version} :autoconf}]
  (when (= type "autotools")
    (let [autoconf-version (or autoconf-version "")
          configurations (or configurations [])
          srcdir (or srcdir "")
          automake "automake"]
      (autotools-generate srcdir)
      (shell-out "make" :dir srcdir)
      (shell-out "make" "check" :dir srcdir))))


(defn download
  "Download a file, and save it to a known filename"
  [remote-file local-file]
  (shell-out "curl" remote-file "-o" local-file "-s")
 ; (shell-out "cp" "scratch/saved.tar.bz2" local-file)
  )

(defn untar
  "Untar a file, into directory"
  [tar-file directory]
  (shell-out "tar" "jxf" tar-file "-C" directory)
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

(defn try-hg
  [url]
  (let [dir "scratch/"
        tar-file (str dir "tip.tar.bz2")]
    (download (str url "/archive/tip.tar.bz2") tar-file)
    (untar tar-file dir)
    {:srcdir (fs/join dir (tar-get-directory tar-file))}))


; TODO: github has the download link, that might be faster still
(defn try-github
  [url]
  (let [parsed (re-find #"^git@github.com:(\w+)/(\w+)\.git$" url)]
    (when parsed
      (let [[_ username project] parsed
            dir (fs/join "scratch" project)]
        (shell-out "mkdir" "-p" dir)
        (shell-out "git" "init" :dir dir)
        (shell-out "git" "remote" "add" "origin" url :dir dir)
        (shell-out "git" "fetch" "origin" "HEAD" "--depth" "1" "-q" :dir dir)
        (shell-out "git" "pull" "origin" "HEAD" "-q" :dir dir)
        {:srcdir dir}))))


(defn repo-handler
  [{:keys [repo subdir] :or {subdir ""} :as config}]
  ; if they dont match, return non-nil and try the next one. If they
  ; do match, return the directory the code was checked out into on
  ; success, and raise an exception on failure.
  (let [{:keys [srcdir]} (some #(% repo) [try-github try-hg])]
    (into config {:srcdir (fs/join srcdir subdir)})))


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


(defn clean-scratch-directory
  []
  (shell-out "rm" "-Rf" "scratch/"))

(defn init [& argv]
  (circle.repl/init)
  (circle.db/init)
  (clean-scratch-directory)
  (let [configuration (-> argv
                          first
                          io/reader
                          slurp
                          yaml/parse-string)]
    (process-configurations configuration)))


(defn -main []
  (init *command-line-args*))
