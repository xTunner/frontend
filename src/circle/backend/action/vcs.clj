(ns circle.backend.action.vcs
  "build actions for checking out code."
  (:require [circle.backend.action.bash :as bash])
  (:require [circle.model.build :as build])
  (:require [circle.sh :as sh])
  (:require fs)
  (:use [arohner.utils :only (inspect)])
  (:use [circle.backend.action :only (defaction abort!)])
  (:use [circle.backend.action.bash :only (remote-bash-build)])
  (:use [circle.backend.action.user :only (home-dir)])
  (:use [circle.backend.github-url :only (->ssh)])
  (:use [circle.util.except :only (throw-if-not)])
  (:use [clojure.tools.logging :only (infof)])
  (:require [circle.backend.git :as git])
  (:require [circle.backend.ssh :as ssh])
  (:use [circle.util.core :only (apply-if)])
  (:require [clj-time.core :as time])
  (:use midje.sweet)
  (:require [clj-url.core :as url]))

(defn vcs-type
  "returns the VCS type for a given url. Returns one of :git, :hg, :svn or nil, if unknown"
  [url]
  (letfn [(match [re]
            (re-find re url))]
    (cond
     (match #"^https://github.com") :git
     (match #"^git@github.com") :git
     (= (-> url url/parse :protocol) "git") :git
     :else nil)))

(def remote-git-ssh-path "bin/git_ssh.sh") ;; where we'll put the git_ssh.sh script on the remote box

(defn ensure-git-ssh
  "Makes sure the git-ssh script is on the remote box"
  [build]
  (let [node (-> @build :node)]
    (ssh/remote-exec node "mkdir -p ~/bin")
    (ssh/scp node
             :direction :to-remote
             :local-path git/git-ssh-path
             :remote-path "bin/git_ssh.sh")
    (ssh/remote-exec node "chmod +x bin/git_ssh.sh")))

(defn private-key-name [build]
  (format "%s.id_rsa" (clojure.string/replace (build/build-name build) #" " "-")))

(defn private-key-path [build]
  (format ".ssh/%s" (private-key-name build)))

(defn ensure-ssh-keys
  "Makes sure the ssh keys necessary to check out the project are on the remote box"
  [build]
  (when (-> @build :vcs-private-key)
    (let [node (-> @build :node)]
      (ssh/remote-exec node "mkdir -p ~/bin")
      (git/with-temp-ssh-key-file [file (-> @build :vcs-private-key)]
        (ssh/scp node
                 :direction :to-remote
                 :local-path (str file)
                 :remote-path (private-key-path build))))))

(defmulti checkout-impl (fn [{:keys [vcs url path]}]
                          vcs))

(defmethod checkout-impl :git [{:keys [build url path revision]}]
  (throw-if-not (pos? (.length url)) "url must be non-empty")
  (throw-if-not (pos? (.length path)) "path must be non-empty")
  (ensure-git-ssh build)
  (ensure-ssh-keys build)
  (println "checking out" url " to " path)
  (let [cmd-env {"SSH_ASKPASS" false}
        cmd-env (apply-if (-> @build :vcs-private-key) merge cmd-env {"GIT_SSH" remote-git-ssh-path
                                                                      "GIT_SSH_KEY" (private-key-path build)})
        checkout-cmd (if revision
                       (sh/q
                        (git clone ~url ~path --no-checkout)
                        (cd ~path)
                        (git checkout ~revision))
                       (sh/q
                        (git clone ~url ~path --depth 1)))]
    (remote-bash-build build checkout-cmd
                       :environment cmd-env
                       :absolute-timeout (time/minutes 10))))

(defmethod checkout-impl :default [{:keys [vcs]}]
  (throw (Exception. "don't know how to check out code of type" vcs)))

(defn checkout-dir [b]
  (fs/join (home-dir b) (build/checkout-dir b)))

(defaction checkout []
  {:name "checkout"}
  (fn [build]
    (let [dir (checkout-dir build)
          result (-> (checkout-impl {:build build
                                     :url (->ssh (-> @build :vcs_url))
                                     :path dir
                                     :vcs (-> @build :vcs_url vcs-type)
                                     :revision (-> @build :vcs_revision)}))]
      (when (not= 0 (-> result :exit))
        (abort! build "checkout failed"))
      result)))