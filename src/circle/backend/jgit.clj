(ns circle.backend.jgit
  (:import org.eclipse.jgit.api.Git)
  (:import java.io.File)
  (:require clj-ssh.ssh)
  (:require circle.backend.ssh)
  (:require [circle.model.project :as project])
  (:require [circle.backend.github-url :as github])
  (:use [circle.util.core :only (apply-map)])
  (:use [arohner.utils :only (inspect)])
  (:import org.eclipse.jgit.storage.file.FileRepository)
  (:import org.eclipse.jgit.transport.JschSession)
  (:import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider))

(def repo-root "repos/")  ;; Root directory where we will check out repos

(defn project-name
  "Infer the project name from the URL"
  [url]
  (last (clojure.string/split url #"/")))

(defn default-repo-path [url]
  (str repo-root (project-name url)))

(defn clone
  "Clone a git repo at url, writing the directory to path"
  [url & {:keys [path username ssh-priv-key]}]
  (let [path (or path (default-repo-path url))
        repo (FileRepository. path)
        cmd (Git/cloneRepository)
        cp (when (and username ssh-priv-key) 
             (.setCredentialsProvider cmd (UsernamePasswordCredentialsProvider. username ssh-priv-key)))]
    (.setURI cmd url)
    (when path
      (.setDirectory cmd (File. path)))
    (.call cmd)))

(defn repo-exists? [path]
  (try
    (Git/open (File. path))
    true
    (catch org.eclipse.jgit.errors.RepositoryNotFoundException e
      false)))

(defn ensure-repo [url & {:keys [path]
                          :as opts}]
  (let [path (or path (default-repo-path url))]
    (if (repo-exists? path)
      true
      (apply-map clone url opts))))

(defonce default-session-factory (org.eclipse.jgit.transport.SshSessionFactory/getInstance))

(def session-factory
  (proxy [org.eclipse.jgit.transport.JschConfigSessionFactory] []
    (getSession [uri credentials-provider fs tms]
      (println "session-factory: " uri)
      (println "session-factory: cp=" credentials-provider)
      (def uri uri)
      (let [username (org.eclipse.jgit.transport.CredentialItem$Username.)
            pass (org.eclipse.jgit.transport.CredentialItem$Password.)
            _ (-> credentials-provider (.get uri (list username pass)))
            username (String. (.getValue username))
            priv-key (String. (.getValue pass))
            _ (when priv-key (println "found key" priv-key))
            session (when priv-key
                      (-> (circle.backend.ssh/session :username username
                                                      :ip-addr (-> uri (str) (clj-url.core/parse) :host)
                                                      :public-key nil
                                                      :private-key priv-key)))]
        (if session
          (do
            (clj-ssh.ssh/connect session)
            (def session session)
            (println "circle session")
            (JschSession. session uri))
          (do
            (println "default session")
            (.getSession default-session-factory uri credentials-provider fs tms)))))))

(org.eclipse.jgit.transport.SshSessionFactory/setInstance session-factory)