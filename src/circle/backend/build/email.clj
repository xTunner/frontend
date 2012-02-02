(ns circle.backend.build.email ; TODO: rename to notify: both file and NS
  (:require [circle.ruby :as ruby])
  (:use [clojure.tools.logging :only (info)])
  (:use [circle.util.straight-jacket])
  (:require [clipchat.rooms])
  (:require [circle.model.build :as build])
  (:require [circle.model.project :as project])
  (:import org.bson.types.ObjectId))


(defn send-email-build-notification [build]
  (ruby/ruby-require :simple_mailer)
  (let [build (ruby/->instance :Build @build)
        class (ruby/get-class :SimpleMailer)]
    (assert build)
    (ruby/send class :post_build_email_hook build)))

(defn build-message [build]
  (ruby/send (ruby/->instance :Build build) :as_email_subject))

(defn github-project-name [project]
  (ruby/send (ruby/->instance :Project project) :github_project_name))

(defn project-url [project]
  (ruby/send (ruby/->instance :Project project) :absolute_url))

(defn build-url [build]
  (ruby/send (ruby/->instance :Build build) :absolute_url))

(defn send-hipchat-message [project message & {:keys [color]
                                               :or {color :yellow}}]
  (let [token (-> :hipchat_api_token project)
        room (-> :hipchat_room project)]
    (when (and token room)
      (clipchat.rooms/message
       token
       {:room_id room
        :from "Circle"
        :notify 1
        :color (name color)
        :message message}))))

(defn send-hipchat-build-notification [build]
  (let [project (build/get-project build)
        message (build-message @build)
        url (build-url @build)
        message (format "<a href='%s'>%s</a>" url message)
        success? (-> @build :failed not)
        color (if success? :green :red)]
    (send-hipchat-message project message :color color)))

(defn send-hipchat-setup-notification [project-id]
  (ruby/ruby-require :project)
  (let [project (project/get-by-id (ObjectId. project-id))
        url (project-url project)
        project-name (github-project-name project)
        message (format  "Hi, welcome to Circle! Hipchat notifications are now enabled for <a href=\"%s\">%s</a>"
                         url
                         project-name)]
    (send-hipchat-message project message)))



(defn notify-build-results [build]
  (ruby/require-rails)
  (ruby/ruby-require :build)
  (ruby/ruby-require :project)
  (straight-jacket (send-email-build-notification build))
  (straight-jacket (send-hipchat-build-notification build)))

(defn send-build-error-email [build error]
  (straight-jacket
   (ruby/require-rails)
   (ruby/ruby-require :simple_mailer)
   (ruby/send (ruby/get-class :SimpleMailer) :build_error_email (-> @build :_id) error)))