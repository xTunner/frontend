(ns circle.util.time
  (:require [clj-time.core :as time]))

(defn ju-now
  "Returns a java.util.Time for now"
  []
  (-> (time/now) (.toDate)))

(defn period-to-s
  "Takes a joda period, returns a human readable string"
  [period]
  (.print (org.joda.time.format.PeriodFormat/getDefault) period))