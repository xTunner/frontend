(ns circle.db.migrations
  (:require [clojure.set :as set])
  (:use circle.db.migration-lib)
  (:use [circle.util.core :only (apply-if)]))

(clear-migrations)

(def-migration "failed? -> failed"
  :num 0
  :coll :builds
  :transform #(set/rename-keys % {:failed? :failed}))

(def-migration "infer empty specs"
  :num 1
  :coll :specs
  :transform (fn [spec]
               (let [empty? (every? empty? ((juxt :dependencies :test :compile :setup) spec))]
                 (println spec "empty?=" empty?)
                 (apply-if empty? assoc spec :inferred true))))

;; (def-migration "old builds w/ git commit info")

"action tags; inferred, infrastructure, spec, test, setup"

"add end time to old builds"


