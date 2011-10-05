(ns circle.backend.action.vcs
  (:require [clj-url.core :as url])
  (:require [circle.backend.action :as action])
  (:require [circle.backend.action.bash :as bash])
  (:use [circle.backend.action.bash :only (remote-bash
                                                 *pwd*)])
  (:use [circle.backend.action.user :only (home-dir)]))

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

(defmethod checkout-impl :git [{:keys [context url path]}]
  (println "checking out" url " to " path)
  (remote-bash context [(git clone ~url ~path)]))

(defmethod checkout-impl :default [{:keys [vcs]}]
  (throw (Exception. "don't know how to check out code of type" vcs)))

(defn checkout-dir [context]
  (str (home-dir context) "/" (-> context :build :project-name) "-" (-> context :build :build-num)))

(defn checkout
  "action to checkout code.

 url - the url where the code is.
 path - the directory where the code should end up

 vcs - (optional), the type of vcs if it can't be inferred from the
     url. Valid options are :git, :hg. Throws if vcs can't be inferred and
      isn't specified."

  [url & {:keys [vcs
                 path]
          :as opts}]
  (let [vcs (or vcs (vcs-type url))
        opts (assoc opts :vcs vcs)]
    (when-not vcs
      (throw (Exception. "vcs not specified and could not be inferred")))
    (action/action 
     :name (format "checkout %s" url path)
     :act-fn (fn [context]
               (let [dir (checkout-dir context)
                     result (-> (checkout-impl {:context context
                                                :url url
                                                :path dir
                                                :vcs vcs})
                                (bash/process-result))]
                 (when (-> result :success)
                   (set! *pwd* dir))
                 result)))))