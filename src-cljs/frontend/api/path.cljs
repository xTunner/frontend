(ns frontend.api.path
  (:require [goog.string :as gstring]))

(defn project-settings [vcs-type org repo]
  (case vcs-type
    "bitbucket" (gstring/format
                  "/api/dangerzone/project/%s/%s/%s/settings"
                  vcs-type org repo)
    "github" (gstring/format
               "/api/v1/project/%s/%s/settings"
               org repo)))

(defn project-plan [vcs-type org repo]
  (case vcs-type
    "bitbucket" (gstring/format
                  "/api/dangerzone/project/%s/%s/%s/plan"
                  vcs-type org repo)
    "github" (gstring/format
               "/api/v1/project/%s/%s/plan"
               org repo)))

(defn build-retry [vcs-type org-name repo-name build-num]
  (case vcs-type
    "bitbucket" (gstring/format
                  "/api/dangerzone/project/%s/%s/%s/%s/retry"
                  vcs-type org-name repo-name build-num)
    "github" (gstring/format
               "/api/v1/project/%s/%s/%s/retry"
               org-name repo-name build-num)))
