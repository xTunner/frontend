(ns circle.model.project
  (:use [circle.util.validation :only (validate defn-v)])
  (:use [circle.util.model-validation :only (validate!)])
  (:require [circle.backend.github-url :as github])
  (:use [circle.util.model-validation-helpers :only (require-keys key-types col-predicate)])
  (:require [somnium.congomongo :as mongo]))

(def project-validation [(require-keys [:name
                                        :vcs-type
                                        :vcs-url
                                        :aws-credentials])
                         (col-predicate :vcs-url #(= :top (github/url-type %)) "github url must be https://github.com/foo/bar")])

(defmethod validate ::Project [tag obj]
  (validate! project-validation obj))

(defn project [& {:keys [name
                         vcs-type
                         vcs-url ;; the url that github will call us on the post-commit hook
                         aws-credentials
                         github-ssh-key ;; an SSH private key authorized to checkout code
                         ami-id
                         actions]
                  :as args}]
  (validate! project-validation args)
  args)

(defn-v insert! [^::Project p]
  (mongo/insert! :projects p))

(defn-v get-by-name [name]
  (mongo/fetch-one :projects :where {:name name}))

(defn-v get-by-url [url]
  (mongo/fetch-one :projects :where {:vcs-url url}))

(defn github-ssh-key-for-url [url]
  (-> url (get-by-url) :githb-ssh-key))