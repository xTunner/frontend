(ns circle.backend.build.inference
  "fns for creating a build from a source tree"
  (:use circle.util.fs)
  (:require [circle.backend.build.template :as template])
  (:require [circle.backend.action :as action])
  (:require circle.backend.build.inference.rails
            circle.backend.build.inference.php))

(def inference-fns {:rails (var circle.backend.build.inference.rails/spec)
                    :php (var circle.backend.build.inference.php/spec)})

(defmethod infer-actions* :clojure [_ repo]
  (circle.backend.build.inference.clojure/spec repo))

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
