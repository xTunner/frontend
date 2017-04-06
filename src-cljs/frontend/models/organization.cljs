(ns frontend.models.organization
  (:require [frontend.config :as config]
            [frontend.utils :as util]
            [clojure.string :as string]))

(defn projects-by-follower
  "Returns a map of users logins to projects they follow."
  [projects]
  (reduce (fn [acc project]
            (let [logins (map :login (:followers project))]
              (reduce (fn [acc login]
                        (update-in acc [login] conj project))
                      acc logins)))
          {} projects))

(defn show-upsell?
  "Given an org, returns whether or not to show that org upsells"
  [org]
  (and (not (config/enterprise?)) (> 4 (:num_paid_containers org))))

(defn uglify-org-id
  "Takes a pretty org id (e.g. \"GitHub/circleci\" and returns an org
  id of the form [:vcs_type :username]"
  [org-id-pretty]
  (let [[vcs_type-pretty username-pretty] (string/split
                                           org-id-pretty
                                           #"/")]
    [(util/keywordize-pretty-vcs_type vcs_type-pretty)
     (keyword username-pretty)]))

(defn prettify-org-id
  "Takes an org id of the form [:vcs_type :username] and returns a
  pretty org id (e.g. \"GitHub/circleci\""
  [[vcs_type username]]
  (str (util/prettify-vcs_type vcs_type)
       "/"
       (name username)))

(defn login
  "Takes an org, returns its name.
  Not named 'name' because it conflicts with clojure.core/name"
  [org]
  (or (:login org)
      (:name org)))

(defn same?
  "Compares two orgs, returns whether or not they are the same by
  comparing their name and their vcs_type"
  [org-a org-b]
  (and (= (login org-a) (login org-b)
          (= (:vcs_type org-a) (:vcs_type org-b)))))
