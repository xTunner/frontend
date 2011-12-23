(ns circle.backend.build.inference
  "fns for creating a build from a source tree"
  (:require [circle.backend.build.template :as template]))

(defn infer-repo-type
  "Attempts to figure out what kind of project this repo is. Returns a
  keyword, such as :rails, or nil"
  [repo]
  :rails) ;;FIXME, always returns rails right now


(defmulti infer-actions* (fn [type repo-path]
                           type))

(defmulti node* identity)

(defn node
  "Takes the path to a repo. Returns the node spec to use for this
  build. "
  [repo]
  (let [repo-type (infer-repo-type repo)]
    (node* repo-type)))

(defn infer-actions
  "Dispatches on repo type returned from (infer-repo-type). Returns a
 seq of build actions."
  [repo]
  (let [repo-type (infer-repo-type repo)]
    (template/apply-template :build (infer-actions* repo-type repo))))