(ns circle.util.time
  (:require [clj-time.core :as time]))

(defn java-now
  "Returns a java.util.Time for now"
  []
  (-> (time/now) (.toDate)))

(defn period-to-s
  "Takes a joda period, returns a human readable string"
  [period]
  (.print (org.joda.time.format.PeriodFormat/getDefault) period))

(defn to-millis
  "Takes a joda period, returns a number of millis. Assumes the period starts from now."
  [period]
  (.toDurationMillis (time/interval (time/now) (time/plus (time/now) period))))