(ns circle.backend.git
  "fns for interacting with git."
  (:import java.io.File)
  (:require [clojure.string :as str])
  (:use [circle.util.except :only (throw-if-not)])
  (:use [clojure.tools.logging :only (infof)])
  (:use [arohner.utils :only (inspect)])
  (:use [circle.util.args :only (require-args)])
  (:use [clojure.string :only (split)])
  (:require fs)
  (:require [circle.sh :as sh]))

(def repo-root "repos")  ;; Root directory where we will check out repos

(defn locking-repo*
  "Takes a repo path, and a fn of no args. Calls f while locking the repo"
  [repo f]
  (throw-if-not (instance? String repo))
  (locking (.intern repo)
    (f)))

(defmacro with-repo-lock [repo & body]
  `(locking-repo* ~repo
                  (fn []
                    ~@body)))

(defn project-name
  "Infer the project name from the URL"
  [url]
  {:pre [url]}
  (last (clojure.string/split url #"/")))

(defn repo-exists? [path]
  (let [git-dir (fs/join path ".git")]
    (->
     (sh/shq [(stat ~path)])
     (:exit)
     (= 0))))

(defn default-repo-path [url]
  {:pre [url]}
  (fs/join repo-root (project-name url)))

(defmacro with-temp-ssh-key-file
  "Writes the ssh-key to a temp file, executes body. f is the name of the variable that will hold the File for the ssh-key. Deletes the temp key file when done"
  [[f key] & body]
  `(let [key# ~key
         ~f (when key#
              (File/createTempFile "key" "rsa"))]
     (when key#
       (spit ~f key#))
     (try
       ~@body
       (finally
        (when ~f
          (.delete ~f))))))

(def git-ssh-path (fs/abspath "git_ssh.sh"))

(defn git-fn*
  "Takes a seq of stevedore code. Executes it locally, with GIT_SSH and GIT_SSH_KEY set up."
  [steve & {:keys [ssh-key repo]}]
  (require-args steve repo)
  (with-temp-ssh-key-file [f ssh-key]
    (let [result (with-repo-lock repo
                   (sh/sh steve
                          :environment (when ssh-key
                                         {"GIT_SSH" git-ssh-path
                                          "GIT_SSH_KEY" f})
                          :pwd repo))]
      (throw-if-not (zero? (-> result :exit)) "git command %s returned %s: %s" (first steve) (-> result :exit) (-> result :err))
      result)))

(defn clone
  "Clone a git repo at url, writing the directory to path"
  [url & {:keys [path ssh-key]}]
  (let [path (or path (default-repo-path url))]
    (infof "git clone %s %s" url path)
    ;; all of the other git commands run in the repo working
    ;; directory, i.e. repos/CircleCI. mkdir our clone destination and
    ;; cd to it, for symmetry. This also simplifies the
    ;; with-repo-lock.
    (fs/mkdirs path)
    (git-fn* (sh/q
              (git clone ~url "."))
             :ssh-key
             ssh-key
             :repo path)))

(defn checkout
  "git checkout cmd"
  [repo revision]
  (infof "git checkout %s" repo)
  (git-fn* (sh/q
            (git checkout ~revision))
           :repo repo))

(defn pull
  "Git pull an existing repo"
  [repo & {:keys [ssh-key]}]
  (infof "git pull %s" repo)
  (git-fn* (sh/q
            (git pull))
           :ssh-key ssh-key
           :repo repo))

(defn ensure-repo
  "Ensures a repo is present, or clones it if not. Also updates the
  repo to the newest code unless :update is false"
  [url & {:keys [path ssh-key update]
          :or {update true}}]
  (let [path (or path (default-repo-path url))]
    (if (repo-exists? path)
      (when update
        (checkout path "master") ;; you have to be on a branch to pull
        (pull path :ssh-key ssh-key))
      (clone url :path path :ssh-key ssh-key))))

(defn format-log
  "Returns the output of git log --pretty=format:F"
  [repo commit f]
  (->
   (git-fn* (sh/q (git log -1 ~commit ~(str "--pretty=format:%" (name f))))
            :repo repo)
   :out
   (str/trim)))

(defn name-rev
  "Return the revision name for a commit"
  [repo commit]
  (->
   (git-fn* (sh/q (git name-rev "--name-only" ~commit))
            :repo repo)
   :out
   (str/trim)))

(defn latest-local-commit
  "Returns the most recent commit id, on the current branch."
  [repo]
  {:post [(do (infof "latest commit for %s is %s" repo %) true)]}
  (format-log repo :HEAD :H))

(defn commit-details
  "Returns a map of important commit information"
  [repo commit]
  (let [f #(format-log repo commit %)]
    (->
     ;; In the order of the git-log man page
     {:parents (-> :P f (split #" "))
      :author_name (f :an)
      :author_email (f :ae)
      :author_date (f :at)
      :committer_name (f :cn)
      :committer_email (f :ce)
      :committer_date (f :ct)
      :subject (f :s)
      :body (f :b)
      :branch (name-rev repo commit)})))

(defn latest-remote-commit
  "Returns the most recent on origin/master. Does not fetch."
  [repo]
  (format-log repo "origin/master" :H))

(defn committer-email
  "Returns the email address of the committer"
  [repo commit]
  (-> (format-log repo commit :ae) (str/trim)))