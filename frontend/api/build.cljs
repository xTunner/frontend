(ns frontend.api.build
  (:require [frontend.models.build :as build-model]
            [frontend.utils :as utils :include-macros true]
            [frontend.utils.vcs-url :as vcs-url]
            [goog.string :as gstring]
            [goog.string.format]))

(defn get-usage-queue [build api-ch]
  (utils/ajax :get
              (gstring/format "/api/v1/project/%s/%s/%s/usage-queue"
                              (vcs-url/org-name (:vcs_url build))
                              (vcs-url/repo-name (:vcs_url build))
                              (:build_num build))
              :usage-queue
              api-ch
              :context (build-model/id build)))
