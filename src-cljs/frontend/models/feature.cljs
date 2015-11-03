(ns frontend.models.feature
  "Functions related to enabling and disabling features."
  (:require [goog.net.cookies :as cookies]
            [frontend.models.project :as project]
            [frontend.utils :as util]
            [frontend.utils.launchdarkly :as ld])
  (:import goog.Uri))

(defn enabled-for-project? [project feature]
  (project/feature-enabled? project feature))

;; export so we can set this using javascript in production

(defn ^:export enabled?
  "If a feature is enabled or disabled in the query string, use that
  value, otherwise look in a cookie for the feature. Returns false by
  default."
  [feature]
  (ld/feature-on? (name feature)))
