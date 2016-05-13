(ns frontend.utils.vcs
  (:require [frontend.models.feature :as feature]
            [frontend.models.user :as user]))

(defn bitbucket-enabled? [user]
  (or (feature/enabled? :bitbucket)
      (user/bitbucket-authorized? user)))
