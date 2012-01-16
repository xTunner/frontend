(ns circle.model.spec
  (:require [somnium.congomongo :as mongo]))

(def spec-coll :specs)

(defn get-spec-for-project [project]
  (if (not (nil? (-> project :setup)))
    (select-keys project [:setup :dependencies :compile :test])
    (-> (mongo/fetch-one spec-coll :where {:project_id (:_id project)})
        (dissoc :version :versions))))