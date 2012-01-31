(ns circle.model.spec
  (:require [somnium.congomongo :as mongo]))


(defn get-spec-for-project [project]
  (select-keys project [:setup :dependencies :compile :test :extra]))