(ns frontend.utils.vcs
  (:require [frontend.models.feature :as feature]
            [frontend.state :as state]
            [frontend.models.user :as user]))

(defn bitbucket-enabled? []
  (or (feature/enabled? :bitbucket)
      (user/bitbucket-authorized? (get-in @state/debug-state state/user-path))))
