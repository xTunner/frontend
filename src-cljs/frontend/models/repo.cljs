(ns frontend.models.repo
  (:require [clojure.set :as set]
            [frontend.datetime :as datetime]
            [goog.string :as gstring]
            goog.string.format))

(defn building-on-circle? [repo]
  (or (:following repo)
      (:has_followers repo)))

(defn should-do-first-follower-build? [repo]
  (and (not (building-on-circle? repo))
       (:admin repo)))

(defn requires-invite? [repo]
  (and (not (building-on-circle? repo))
       (not (:admin repo))))

(defn can-follow? [repo]
  (and (not (:following repo))
       (or (:admin repo)
           (building-on-circle? repo))))

(defn likely-osx-repo? [repo]
  (let [osx-languages #{"Swift" "Objective-C" ; GH
                        "swift" "objective-c" ; BB
                        }]
    (contains? osx-languages (:language repo))))

(defn id [repo]
  (:vcs_url repo))
