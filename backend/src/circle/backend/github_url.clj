(ns circle.backend.github-url
  (:use midje.sweet)
  (:use [clj-url.core :as url])
  (:use [circle.util.core :only (apply-if)])
  (:require [clojure.string :as str])
  (:use [arohner.utils :only (inspect)]))

;; functions for converting between the various github urls
;;
;; Some definitions:
;;
;; top-level, the http url you visit in a browser, like https://github.com/arohner/circleci
;;
;; ssh-url, the url used for ssh, like git@github.com:arohner/CircleCI.git
;;
;; https-url: used for git over http, https://arohner@github.com/arohner/CircleCI.git

(defn github?
  "True if this URL belongs to github.com"
  [url]
  (-> url (url/parse) :host (->> (re-find #"github.com")) (boolean)))

(defn url-type
  "returns :top, :ssh or :http or nil if unrecognized"
  [u]
  (cond
   (re-find #"^http(s)?://[^@]+(\.git){0}$" u) :top
   (re-find #"^git@" u) :ssh
   (re-find #"^http(s)?://.*@.*\.git$" u) :http
   :else nil))

(tabular
 (fact "url-type works"
   (url-type ?url) => ?expected)
 ?url ?expected
 "https://github.com/arohner/CircleCI.git" :top
 "https://github.com/arohner/CircleCI" :top
 "git@github.com:arohner/CircleCI.git" :ssh
 "https://arohner@github.com/arohner/CircleCI.git" :http)

(defn ->ssh
  "Given a github http url, return the ssh version.

  https://github.com/arohner/CircleCI.git -> git@github.com:arohner/CircleCI.git"
  [url]
  (letfn [(replace-leading-slash [s]
          (str/replace s #"^/" ""))]
    (condp = (url-type url)
      :ssh url
      :top (let [path (-> url (url/parse) :path (replace-leading-slash))]
             (str "git@github.com:" path ".git"))
      :http (let [path (-> url (url/parse) :path (replace-leading-slash))]
              (str "git@github.com:" path)))))

(tabular
 (fact "->ssh works"
   (->ssh ?url) => ?expected)
 ?url ?expected
 "https://github.com/arohner/CircleCI" "git@github.com:arohner/CircleCI.git"
 "git@github.com:arohner/CircleCI.git" "git@github.com:arohner/CircleCI.git"
 "https://arohner@github.com/arohner/CircleCI.git" "git@github.com:arohner/CircleCI.git")

(defn ->top [url]
  (letfn [(strip-git [s]
            (str/replace s #".git$" ""))]
    (condp = (url-type url)
      :top url
      :ssh (let [path (-> url (clj-url.core/parse) :path (strip-git))]
             (str "https://github.com/" path))
      :http (let [path (-> url (clj-url.core/parse) :path (strip-git))]
              (str "https://github.com" path)))))

(tabular
 (fact "->top works"
   (->top ?url) => ?expected)
 ?url ?expected
 "https://github.com/arohner/CircleCI" "https://github.com/arohner/CircleCI"
 "git@github.com:arohner/CircleCI.git" "https://github.com/arohner/CircleCI" 
 "https://arohner@github.com/arohner/CircleCI.git" "https://github.com/arohner/CircleCI")