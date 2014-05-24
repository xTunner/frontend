(ns frontend.models.container
  (:require [clojure.set :refer (intersection)]
            [frontend.datetime :as datetime]
            [goog.string :as gstring]
            goog.string.format))

(defn id [container]
  (:index container))

(defn status-classes [container]
  (let [action-statuses (->> container :actions (map :status) (remove nil?) set)]
    (concat []
            (when (or (contains? action-statuses "running")
                      (empty? action-statuses))
              ["running"])
            (when (seq (intersection #{"failed" "timedout" "cancelled" "infrastructure_fail"}
                                     action-statuses))
              ["failed"])
            (when (some :canceled (:actions container))
              ["canceled"])
            (when (= #{"success"} action-statuses)
              ["success"]))))
