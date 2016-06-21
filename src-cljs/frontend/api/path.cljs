(ns frontend.api.path
  (:require [goog.string :as gstring]))

(defn- base-project-url-path [vcs-type]
  (case vcs-type
    "bitbucket" (gstring/format
                  "/api/dangerzone/project/%s"
                  vcs-type)
    "github" "/api/v1/project"))

(defn- base-organization-url-path [vcs-type org-name]
  (gstring/format "/api/v1.1/organization/%s/%s"
                  (or vcs-type "github")
                  org-name))

(defn branch-path [vcs-type org-name repo-name branch]
  (gstring/format
   "%s/%s/%s/tree/%s"
   (base-project-url-path vcs-type) org-name repo-name (gstring/urlEncode branch)))

(defn project-settings [vcs-type org-name repo-name]
  (gstring/format
   "%s/%s/%s/settings"
   (base-project-url-path vcs-type) org-name repo-name))

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

(defn project-plan [vcs-type org-name repo-name]
  (gstring/format
   "%s/%s/%s/plan"
   (base-project-url-path vcs-type) org-name repo-name))

(defn project-tokens  [vcs-type project-name]
  (gstring/format
   "%s/%s/token"
   (base-project-url-path vcs-type) project-name))

(defn project-token [vcs-type project-name token]
  (gstring/format
   "%s/%s/token/%s"
   (base-project-url-path vcs-type) project-name token))

(defn project-follow [vcs-type project]
  (gstring/format
   "%s/%s/follow"
   (base-project-url-path vcs-type) project))

(defn project-unfollow [vcs-type project]
  (gstring/format
   "%s/%s/unfollow"
   (base-project-url-path vcs-type) project))

(defn project-enable [vcs-type project]
  (gstring/format
   "%s/%s/enable"
   (base-project-url-path vcs-type) project))

(defn project-users [vcs-type project-name]
  (gstring/format "%s/%s/users"
                  (base-project-url-path vcs-type)
                  project-name))

(defn project-users-invite [vcs-type project-name]
  (gstring/format "%s/%s/users/invite"
                  (base-project-url-path vcs-type)
                  project-name))

(defn organization-invite [vcs-type org-name]
  (gstring/format "%s/invite"
                  (base-organization-url-path vcs-type org-name)))

(defn build-retry [vcs-type org-name repo-name build-num]
  (gstring/format
   "%s/%s/%s/%s/retry"
   (base-project-url-path vcs-type) org-name repo-name build-num))

(defn build-cancel [vcs-type org-name repo-name build-num]
  (gstring/format
   "%s/%s/%s/%s/cancel"
   (base-project-url-path vcs-type) org-name repo-name build-num))

(defn heroku-deploy-user [vcs-type repo-name]
  (gstring/format
   "%s/%s/heroku-deploy-user"
   (base-project-url-path vcs-type) repo-name))

(defn action-output [vcs-type project-name build-num step index max-chars]
  (gstring/format "%s/%s/%s/output/%s/%s?truncate=%s"
                  (base-project-url-path vcs-type)
                  project-name
                  build-num
                  step
                  index
                  max-chars))

(defn action-output-file [vcs-type project-name build-num step index]
  (gstring/format "%s/%s/%s/output/%s/%s?file=true"
                  (base-project-url-path vcs-type)
                  project-name
                  build-num
                  step
                  index))

(defn artifacts [vcs-type project-name build-num]
  (gstring/format "%s/%s/%s/artifacts"
                  (base-project-url-path vcs-type)
                  project-name
                  build-num))
