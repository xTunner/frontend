(ns frontend.api.path
            (:require [goog.string :as gstring]))

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
