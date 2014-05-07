(ns frontend.models.project
  (:require [goog.string :as gstring]))

(defn project-name [project]
  (subs (:vcs_url project) 19 (count (:vcs_url project))))

(defn path-for [project & [branch]]
  (str "/gh/" (project-name project)
       (when branch
         (str "/tree/" (gstring/urlEncode branch)))))

(defn settings-path [project]
  (str "/gh/" (project-name project) "/edit"))

(defn default-branch? [branch-name project]
  (= branch-name (:default_branch project)))

(defn personal-branch? [user project branch-name branch]
  (some #{(:login user)} (:pusher_logins branch)))

(defn personal-branches [user project]
  (filter (fn [[name-kw branch]]
            (or
             (personal-branch? user project (name name-kw) branch)
             (default-branch? (name name-kw) project)))
          (:branches project)))

(defn show-toggle-branches? [user project]
  (first (remove (fn [[name-kw branch]]
                   (personal-branch? user project (name name-kw) branch))
                 (:branches project))))

(defn id [project]
  (:vcs_url project))
