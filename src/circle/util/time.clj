(ns circle.util.time
  (:require [clj-time.core :as time]))

(defn ju-now
  "Returns a java.util.Time for now"
  []
  (-> (time/now) (.toDate)))