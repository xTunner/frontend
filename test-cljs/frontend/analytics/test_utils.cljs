(ns frontend.analytics.test-utils
  (:require [cljs.test :refer-macros [is deftest testing]]
            [frontend.utils.seq :refer [submap?]]))

(defn owner [{:keys [view user repo org state]}]
  (atom
    (merge {:current-user {:login (or user "test-user")}
            :navigation-point (or view "test-view")
            :navigation-data {:repo (or repo "test-view")
                              :org (or org "test-org")}}
           state)))

(defn current-state [{:keys [view user repo org state]}]
  @(owner {:view view
           :user user
           :repo repo
           :org org
           :state state}))
