(ns frontend.analytics.utils
  (:require [frontend.utils.vcs-url :as vcs-url]))

(defn canonical-plan-type
  "Stop gap until we switch :paid to :linux"
  [plan-type]
  (if (= plan-type :paid)
    :linux
    plan-type))

(defn project-properties [project]
  (let [vcs-url (:vcs_url project)]
    {:vcs-type (vcs-url/vcs-type vcs-url)
     :org (vcs-url/org-name vcs-url)
     :repo (vcs-url/repo-name vcs-url)}))
