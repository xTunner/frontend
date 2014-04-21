(ns frontend.models.action
  (:require [frontend.datetime :as datetime]
            [frontend.models.project :as proj]
            [goog.string :as gstring]
            goog.string.format))

(defn id [action]
  )

(defn failed? [action]
  (#{"failed" "timedout" "cancelled" "infrastructure_fail"} (:status action)))

(defn has-content? [action]
  (or (:has_output action)
      (:bash_command action)
      (seq (:final-out action))))

(defn duration [{:keys [start_time stop_time] :as action}]
  (cond (:run_time_millis action) (datetime/as-duration (:run_time_millis action))
        (:start_time action) (datetime/as-duration (- (.getTime (js/Date.))
                                                      (js/Date.parse start-time)))
        :else nil))
