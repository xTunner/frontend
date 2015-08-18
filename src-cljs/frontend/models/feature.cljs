(ns frontend.models.feature
  (:require [frontend.models.project :as project]
            [frontend.utils :as util])
  (:import goog.Uri))

(defn enabled-for-project? [project feature]
  (project/feature-enabled? project feature))

(defn feature-flag-value-true? [value]
  (= value "true"))

(defn enabled-in-query-string? [feature]
  (-> js/location
      str
      goog.Uri.parse
      .getQueryData
      (.get (str "feature-" (name feature)))
      feature-flag-value-true?))
