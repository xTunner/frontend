(ns frontend.api.user
  (:require [frontend.models.user :as user-model]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring]
            [goog.string.format]))

(defn get-projects [api-ch]
  (utils/ajax :get "/api/v1/projects" :projects api-ch))
