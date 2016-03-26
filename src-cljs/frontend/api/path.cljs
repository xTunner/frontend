(ns frontend.api.path
  (:require
   [frontend.models.action :as action-model]
   [goog.string :as gstring]))

(defn base-project-url-path [vcs-type]
  (case vcs-type
    "bitbucket" (gstring/format
                  "/api/dangerzone/project/%s"
                  vcs-type)
    "github" "/api/v1/project"))

(defn project-settings [vcs-type org repo]
  (gstring/format
   "%s/%s/%s/settings"
   (base-project-url-path vcs-type) org repo))

(defn project-checkout-keys [vcs-type repo-name]
  (gstring/format
   "%s/%s/checkout-key"
   (base-project-url-path vcs-type)
   repo-name))

(defn project-checkout-key  [vcs-type repo-name fingerprint]
  (gstring/format
   "%s/%s/checkout-key/%s"
   (base-project-url-path vcs-type) repo-name fingerprint))

(defn project-ssh-key  [vcs-type repo-name]
  (gstring/format
   "%s/%s/ssh-key"
   (base-project-url-path vcs-type) repo-name))

(defn project-plan [vcs-type org repo]
  (gstring/format
   "%s/%s/%s/plan"
   (base-project-url-path vcs-type) org repo))

(defn project-tokens  [vcs-type project-name]
  (gstring/format
   "%s/%s/token"
   (base-project-url-path vcs-type) project-name))

(defn project-token [vcs-type project-name token]
  (gstring/format
   "%s/%s/token/%s"
   (base-project-url-path vcs-type) vcs-type project-name token))

(defn build-retry [vcs-type org-name repo-name build-num]
  (gstring/format
   "%s/%s/%s/%s/retry"
   (base-project-url-path vcs-type) org-name repo-name build-num))

(defn heroku-deploy-user [vcs-type repo-name]
  (gstring/format
   "%s/%s/heroku-deploy-user"
   (base-project-url-path vcs-type) repo-name))

(defn action-output [vcs-type project-name build-num step index]
  (gstring/format "%s/%s/%s/output/%s/%s?truncate=%s"
                  (base-project-url-path vcs-type)
                  project-name
                  build-num
                  step
                  index
                  action-model/max-output-size))

(defn action-output-file [vcs-type project-name build-num step index]
  (gstring/format "%s/%s/%s/output-file/%s/%s"
                  (base-project-url-path vcs-type)
                  project-name
                  build-num
                  step
                  index))
