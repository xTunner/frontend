(ns frontend.models.project
  (:require [frontend.utils :as utils :include-macros true]
            [goog.string :as gstring]))

(defn project-name [project]
  (subs (:vcs_url project) 19 (count (:vcs_url project))))

(defn path-for [project & [branch]]
  (str "/gh/" (project-name project)
       (when branch
         (str "/tree/" (gstring/urlEncode branch)))))

(defn settings-path [project]
  (str "/gh/" (project-name project) "/edit"))

(defn default-branch? [branch-name project]
  (= (name branch-name) (:default_branch project)))

(defn personal-branch? [user project branch-data]
  (let [[branch-name build-info] branch-data]
    (or (default-branch? branch-name project)
        (some #{(:login user)} (:pusher_logins build-info)))))


;; XXX: this fn can probably go away
(defn personal-branches [user project]
  (filter (fn [branch-info]
            (or
             (personal-branch? user project branch-info)
             (default-branch? (name (first branch-info)) project)))
          (:branches project)))

(defn branch-builds [project branch-name]
  (let [build-data (get-in project [:branches branch-name])]
    (sort-by :build_num (concat (:running_builds build-data)
                                (:recent_builds build-data)))))

(defn master-builds
  "Returns branch builds for the project's default branch (usually master)"
  [project]
  (branch-builds project (:personal_branch project)))

(defn notification-settings [project]
  (select-keys project [:hipchat_room
                        :hipchat_api_token
                        :hipchat_notify
                        :hipchat_notify_prefs
                        :slack_channel
                        :slack_subdomain
                        :slack_api_token
                        :slack_notify_prefs
                        :slack_webhook_url
                        :hall_room_api_token
                        :hall_notify_prefs
                        :campfire_room
                        :campfire_token
                        :campfire_subdomain
                        :campfire_notify_prefs
                        :flowdock_api_token
                        :irc_server
                        :irc_channel
                        :irc_keyword
                        :irc_username
                        :irc_password
                        :irc_notify_prefs]))

(defn id [project]
  (:vcs_url project))
