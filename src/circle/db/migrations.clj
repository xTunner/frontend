(ns circle.db.migrations
  (:require [clojure.set :as set])
  (:use circle.db.migration-lib)
  (:require [somnium.congomongo :as mongo])
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
                 (apply-if empty? assoc spec :inferred true))))

(def-migration "move specs into project"
  :num 2
  :coll :projects
  :transform (fn [p]
               (assert (= 1 (mongo/fetch :specs :where {:project_id (:_id p)} :count? true)))
               (let [spec (mongo/fetch-one :specs :where {:project_id (:_id p)})]
                 (apply-if spec merge p (select-keys spec [:dependencies :setup :compile :test])))))

;; (def-migration "old builds w/ git commit info")

"action tags; inferred, infrastructure, spec, test, setup"

"add end time to old builds"
