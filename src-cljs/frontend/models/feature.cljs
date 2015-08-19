(ns frontend.models.feature
  (:require [goog.net.cookies :as cookies]
   [frontend.models.project :as project]
            [frontend.utils :as util])
  (:import goog.Uri))

(defn enabled-for-project? [project feature]
  (project/feature-enabled? project feature))

(defn feature-flag-value-true? [value]
  (= value "true"))

(defn feature-flag-key-name [feature]
  (str "feature-" (name feature)))

(defn enabled-in-query-string? [feature]
  (-> js/location
      str
      goog.Uri.parse
      .getQueryData
      (.get (str "feature-" (name feature)))
      feature-flag-value-true?))

(defn enabled-in-cookie? [feature]
  (-> feature
      feature-flag-key-name
      cookies/get
      feature-flag-value-true?))

(defn enable-in-cookie [feature]
  (cookies/set (feature-flag-key-name feature) "true"))

(defn disable-in-cookie [feature]
  (cookies/set (feature-flag-key-name feature) "false"))

(defn enabled? [feature]
  (or (enabled-in-cookie? feature) (enabled-in-query-string? feature)))
