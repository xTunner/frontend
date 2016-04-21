(ns frontend.utils.vcs-url
  (:require [clojure.string :as string]
            [frontend.utils :as utils :include-macros true]
            [frontend.routes :as routes]))

(def url-project-re #"^https?://([^/]+)/(.*)")

(defn project-name [vcs-url]
  (some->> vcs-url
           (re-matches url-project-re)
           (last)))

(defn vcs-type [vcs-url]
  (or ({"github.com" "github"
        "bitbucket.org" "bitbucket"} (second (re-matches url-project-re vcs-url)))
      "github"))

(defmulti profile-url (comp keyword :vcs_type))

;; slashes aren't allowed in github org/user names or project names
(defn org-name [vcs-url]
  (first (string/split (project-name vcs-url) #"/")))

(defn repo-name [vcs-url]
  (second (string/split (project-name vcs-url) #"/")))

(defn display-name [vcs-url]
  (.replace (project-name vcs-url) "/" \u200b))

(defn project-path [vcs-url]
  (str "/" (routes/->short-vcs (vcs-type vcs-url)) "/" (project-name vcs-url)))
