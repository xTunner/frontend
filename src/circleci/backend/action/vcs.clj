(ns circleci.backend.vcs
  (:require [clj-url.core :as url])
  (:require [circleci.backend.action :as action])
  (:use [circleci.backend.action.bash :only (bash)]))

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

(defmulti checkout-impl (fn [{:keys [vcs url path]}]
                          vcs))

(defmulti checkout-impl :git [{:keys [url path]}]
  (bash (format "checkout %s to %s" url path)
        (git clone url path)))

(defn checkout
  "action to checkout code.

 url - the url where the code is.
 path - the directory where the code should end up
 vcs - (optional), the type of vcs if it can't be inferred from the url. Valid options are :git, :hg. Throws if vcs can't be inferred and isn't specified."
  [& {:keys [vcs
             url
             path]
      :as opts}]
  (let [vcs (or vcs (vcs-type url))
        opts (assoc opts :vcs vcs)]
    (when-not vcs
      (throw (Exception. "vcs not specified and could not be inferred")))
    (checkout-impl opts)))