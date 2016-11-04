(ns frontend.analytics.ab
  (:require [clojure.string :as str]
            [frontend.models.feature :as feature]))

(defn ab-test-treatments
  ([]
   (ab-test-treatments feature/ab-test-treatments))
  ([treatment-definitions]
   (->>
    treatment-definitions
    keys
    (map (fn [title]
           [title (-> title feature/ab-test-treatment name)]))
    (into {}))))
