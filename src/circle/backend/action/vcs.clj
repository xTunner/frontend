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

(defmethod checkout-impl :git [{:keys [context url path revision]}]
  (println "checking out" url " to " path)
  (if revision
    (remote-bash context [(git clone ~url ~path --no-checkout)
                          (cd ~path)
                          (git checkout ~revision)])
    (remote-bash context [(git clone ~url ~path --depth 1)])))

(defmethod checkout-impl :default [{:keys [vcs]}]
  (throw (Exception. "don't know how to check out code of type" vcs)))

(defn checkout-dir [context]
  (str (home-dir context) "/" (-> context :build :project-name) "-" (-> context :build :build-num)))

(defn checkout
  "action to checkout code."
  []
  (action/action 
   :name (format "checkout")
   :act-fn (fn [context]
             (let [dir (checkout-dir context)
                   result (-> (checkout-impl {:context context
                                              :url (-> context :build :vcs-url)
                                              :path dir
                                              :vcs (-> context :build :vcs-type)
                                              :revision (-> context :build :vcs-revision)})
                              (bash/process-result))]
               (when (-> result :success)
                 (set! *pwd* dir))
               result))))