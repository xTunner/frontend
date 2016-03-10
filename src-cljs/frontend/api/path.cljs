(ns frontend.api.path
  (:require [goog.string :as gstring]))

; settings
(defn settings-path [project]
  (case (:vcs_type project)
    "bitbucket" (gstring/format
                  "/api/dangerzone/project/%s/%s/%s/settings"
                  (:vcs_type project) (:org project) (:repo project))
    "github" (gstring/format
               "/api/v1/project/%s/%s/settings"
               (:org project) (:repo project))))

(defn settings-plan [project]
  (case (:vcs_type project)
    "bitbucket" (gstring/format
                  "/api/dangerzone/project/%s/%s/%s/plan"
                  (:vcs_type project) (:org project) (:repo project))
    "github" (gstring/format
               "/api/v1/project/%s/%s/plan"
               (:org project) (:repo project))))
; builds
(defn build-retry [{:keys [vcs-type org-name repo-name build-num] :as project}]
  (case vcs-type
    "bitbucket" (gstring/format
                  "/api/dangerzone/project/%s/%s/%s/%s/retry"
                  vcs-type org-name repo-name build-num)
    "github" (gstring/format
               "/api/v1/project/%s/%s/%s/retry"
               org-name repo-name build-num)))
