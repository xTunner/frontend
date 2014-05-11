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
