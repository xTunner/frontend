(ns circle.model.spec
  (:require [somnium.congomongo :as mongo]))

(def spec-coll :specs)

(defn get-spec-for-project [project]
  (-> (mongo/fetch-one spec-coll :where {:project_id (:_id project)})
      (dissoc :version :versions)))