(ns circle.db.migrations
  (:require [clojure.set :as set])
  (:use circle.db.migration-lib)
  (:require [somnium.congomongo :as mongo])
  (:use [clojure.set :only (rename-keys)])
  (:use [circle.util.core :only (apply-if)]))

(clear-migrations)

(def-migration "failed? -> failed"
  :num 0
  :coll :builds
  :transform #(set/rename-keys % {:failed? :failed}))

(def-migration "infer empty specs"
  :num 1
  :coll :projects
  :transform (fn [project]
               (let [spec (mongo/fetch-one :specs :where {:project_id (-> project :_id)})
                     inferred? (or
                                (nil? spec)
                                (every? empty? ((juxt :dependencies :test :compile :setup) spec)))]
                 (assoc project :inferred inferred?))))

(def-migration "move specs into project"
  :num 2
  :coll :projects
  :transform (fn [p]
               (assert (> 2 (mongo/fetch-count :specs :where {:project_id (:_id p)})))
               (let [spec (mongo/fetch-one :specs :where {:project_id (:_id p)})]
                 (apply-if spec merge p (select-keys spec [:dependencies :setup :compile :test :inferred])))))

(def-migration "add start_times to builds, calculated from stop_time and action_logs"
  :num 3
  :coll :builds
  :query {:start_time {:$exists false}}
  :transform (fn [b]
               (let [build_id (-> b :_id)
                     log (mongo/fetch-one :action_logs :where {:_build-ref build_id} :sort {:start_time 1})
                     start-time (-> log :start_time)]
                 (merge b {:start_time start-time}))))


(def-migration "rename _project_id to project_id"
  :num 4
  :coll :builds
  :query {:_project_id {:$exists true}}
  :transform (fn [b]
               (rename-keys b {:_project_id :project_id})))
;; (def-migration "old builds w/ git commit info")

"action tags; inferred, infrastructure, spec, test, setup"

"add end time to old builds"
