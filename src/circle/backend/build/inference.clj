(ns circle.backend.build.inference
  "fns for creating a build from a source tree"
  (:require [circle.backend.build.template :as template])
  (:require [circle.backend.action :as action])
  (:use [arohner.utils :only (inspect)]))

(defn infer-repo-type
  "Attempts to figure out what kind of project this repo is. Returns a
  keyword, such as :rails, or nil"
  [repo]
  :rails) ;;FIXME, always returns rails right now


(defmulti infer-actions* (fn [type repo-path]
                           type))

(defn set-inferred [actions]
  (map #(action/set-source % :inferred) actions))

(defn infer-actions
  "Dispatches on repo type returned from (infer-repo-type). Returns a
 seq of build actions."
  [repo]
  (let [repo-type (infer-repo-type repo)
        actions (set-inferred (infer-actions* repo-type repo))]
    ;; if we do an inference, and come up with nothing useful, don't
    ;; add the template, because that would start nodes. Just return []
    (if (seq actions)
      (template/apply-template :build actions)
      [])))