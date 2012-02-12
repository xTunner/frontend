(ns circle.backend.build.inference
  "fns for creating a build from a source tree"
  (:use circle.util.fs)
  (:require [circle.backend.build.template :as template])
  (:require [circle.backend.action :as action])
  (:require circle.backend.build.inference.rails
            circle.backend.build.inference.php))

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

(def inference-fns {:rails circle.backend.build.inference.rails/spec
                    :php circle.backend.build.inference.php/spec})

(defn set-inferred [actions]
  (map #(action/set-source % :inferred) actions))

(def action-order [:pre-setup :setup :post-setup :pre-test :test :post-test])

(defn merge-actions [specs]
  (->> specs
       (group-by :type)
       ((apply juxt action-order))
       (apply concat)))

(defn infer-actions
  "Dispatches on repo type returned from (infer-repo-type). Returns a
 seq of build actions."
  [repo]
  (let [actions (->> inference-fns
                     (vals)
                     (mapcat #(% repo))
                     (merge-actions)
                     (set-inferred))]
    ;; if we do an inference, and come up with nothing useful, don't
    ;; add the template, because that would start nodes. Just return []
    (if (seq actions)
      (template/apply-template :build actions)
      [])))
