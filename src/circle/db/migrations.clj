(ns circle.db.migrations
  (:require [clojure.set :as set])
  (:use circle.db.migration-lib))

(clear-migrations)

(def-migration "failed? -> failed"
  :coll :builds
  :query {:failed? {"$exists" true}}
  :transform #(set/rename-keys % {:failed? :failed}))

(def-migration "failed? -> failed"
  :coll :builds
  :query {:failed? {"$exists" true}}
  :transform #(set/rename-keys % {:failed? :failed}))

