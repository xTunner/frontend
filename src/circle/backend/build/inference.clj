(ns circle.backend.build.inference
  "fns for creating a build from a source tree"
  (:use circle.util.fs)
  (:require [circle.backend.build.template :as template])
  (:require [circle.backend.action :as action])
  (:require circle.backend.build.inference.rails
            circle.backend.build.inference.php)
  (:use [arohner.utils :only (inspect)]))

(defn dir-contains-php-files?
  "Determines whether or not the repo has any php files."
  [dir]
  (dir-contains-files? dir #"^.*\.php$"))

;;FIXME Assume Rails if not php.
(defn infer-repo-type
  "Attempts to figure out what kind of project this repo is. Returns a
  keyword, such as :rails, or nil"
  [repo]
  (cond
    (dir-contains-php-files? repo) :php
    :else :rails))


(defmulti infer-actions* (fn [type repo-path]
                           type))

(defmethod infer-actions* :rails [_ repo]
  (circle.backend.build.inference.rails/spec repo))

(defmethod infer-actions* :php [_ repo]
  (circle.backend.build.inference.php/spec repo))

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
