(ns circle.db.migrations
  (:require [clojure.set :as set])
  (:use circle.db.migration-lib))

(clear-migrations)

(def-migration "failed? -> failed"
  :num 0
  :coll :builds
  :transform #(set/rename-keys % {:failed? :failed}))