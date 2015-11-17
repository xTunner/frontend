(ns frontend.models.organization
  (:require [frontend.config :as config]))

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
