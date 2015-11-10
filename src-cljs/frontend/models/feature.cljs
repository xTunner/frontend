(ns frontend.models.feature
  "Functions related to enabling and disabling features."
  (:require [goog.net.cookies :as cookies])
  (:import goog.Uri))

(defn- feature-flag-value-true? [value]
  (= value "true"))

(defn- feature-flag-key-name [feature]
  (str "feature-" (name feature)))

(defn- get-in-query-string [feature]
  (-> js/location
      str
      goog.Uri.parse
      .getQueryData
      (.get (str "feature-" (name feature)))))

(defn set-in-query-string? [feature]
  (not (nil? (get-in-query-string feature))))

(defn enabled-in-query-string? [feature]
  (feature-flag-value-true? (get-in-query-string feature)))

(defn- get-in-cookie [feature]
  (-> feature
      feature-flag-key-name
      cookies/get))

(defn enabled-in-cookie? [feature]
  (-> (get-in-cookie feature)
      feature-flag-value-true?))

;; export so we can set this using javascript in production
(defn ^:export enable-in-cookie [feature]
  (cookies/set (feature-flag-key-name feature) "true"))

;; export so we can set this using javascript in production
(defn ^:export disable-in-cookie [feature]
  (cookies/set (feature-flag-key-name feature) "false"))

;; export so we can set this using javascript in production
(defn ^:export enabled?
  "If a feature is enabled or disabled in the query string, use that
  value, otherwise look in a cookie for the feature. Returns false by
  default."
  [feature]
  (if (set-in-query-string? feature)
    (enabled-in-query-string? feature)
    (enabled-in-cookie? feature)))
