(ns frontend.models.container
  (:require [clojure.set :refer (intersection)]
            [frontend.datetime :as datetime]
            [goog.string :as gstring]
            goog.string.format))

(defn id [container]
  (:index container))

(defn status-classes [container build-running?]
  (let [action-statuses (->> container :actions (remove :filler-action) (map :status) (remove nil?) set)]
    (concat []
            (cond (or (= "running" (last action-statuses))
                      (empty? action-statuses))
                  ["running"]

                  build-running? ["waiting"]
                  :else nil)
            (when (seq (intersection #{"failed" "timedout" "cancelled" "infrastructure_fail"}
                                     action-statuses))
              ["failed"])
            (when (some :canceled (:actions container))
              ["canceled"])
            (when (= #{"success"} action-statuses)
              ["success"]))))
