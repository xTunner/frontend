(ns frontend.experimental.github-public-scopes
  (:require [clojure.set :as set]
            [frontend.models.user :as user-model]
            [frontend.utils.github :as gh-utils]
            [frontend.utils :refer-macros [html]]))

(defn add-private-repos-url []
  (gh-utils/auth-url :scope #{"user:email" "repo"}))

(defn current-scopes [user]
  (-> user :github_oauth_scopes set))

;; alternative minimal scopes set for github public scopes
(defn missing-public-scopes [user]
  (let [current-scopes (set (:github_oauth_scopes user))]
    (set/union
      (when (empty? (set/intersection current-scopes #{"user" "user:email"}))
        #{"user:email"})
      (when (empty? (set/intersection current-scopes #{"repo" "public_repo"}))
        #{"public_repo"})
      (when (empty? (set/intersection current-scopes #{"repo" "user" "read:org"}))
        #{"read:org"}))))

(defn has-public-scopes? [user]
  (empty? (missing-public-scopes user)))

(defn has-private-scopes? [user]
  (empty? (user-model/missing-scopes user)))

;; temporary alternative for those missing public scopes and/or private scopes
(defn missing-scopes [user]
  (if (or (has-public-scopes? user)
          (has-private-scopes? user))
    ;; if just missing private scopes, that can be added with add-private-repos
    #{}
    (missing-public-scopes user)))
