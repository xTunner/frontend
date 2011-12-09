(ns circle.backend.action.vcs
  "build actions for checking out code."
  (:require [circle.backend.action.bash :as bash])
  (:require [circle.backend.build :as build])
  (:require [circle.sh :as sh])
  (:require fs)
  (:use [arohner.utils :only (inspect)])
  (:use [circle.backend.action :only (defaction abort!)])
  (:use [circle.backend.action.bash :only (remote-bash-build)])
  (:use [circle.backend.action.user :only (home-dir)])
  (:use [circle.backend.github-url :only (->ssh)])
  (:use [circle.util.except :only (throw-if-not)])
  (:use [clojure.tools.logging :only (infof)])
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

(defmulti checkout-impl (fn [{:keys [vcs url path]}]
                          vcs))

(defmethod checkout-impl :git [{:keys [build url path revision]}]
  (throw-if-not (pos? (.length url)) "url must be non-empty")
  (throw-if-not (pos? (.length path)) "path must be non-empty")
  (println "checking out" url " to " path)
  (let [checkout-cmd (if revision
                       (sh/quasiquote
                        (git clone ~url ~path --no-checkout)
                        (cd ~path)
                        (git checkout ~revision))
                       (sh/quasiquote
                        (git clone ~url ~path --depth 1)))]
    (remote-bash-build build checkout-cmd
                       :environment {"SSH_ASKPASS" false})))

(defmethod checkout-impl :default [{:keys [vcs]}]
  (throw (Exception. "don't know how to check out code of type" vcs)))

(defn checkout-dir [b]
  (fs/join (home-dir b) (build/build-name b)))

(defaction checkout []
  {:name "checkout"}
  (fn [build]
    (let [dir (checkout-dir build)
          result (-> (checkout-impl {:build build
                                     :url (->ssh (-> @build :vcs_url))
                                     :path dir
                                     :vcs (-> @build :vcs_url vcs-type)
                                     :revision (-> @build :vcs-revision)}))]
      (when (not= 0 (-> result :exit))
        (abort! build "checkout failed"))
      result)))