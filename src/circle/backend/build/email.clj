(ns circle.backend.build.email ; TODO: rename to notify: both file and NS
  (:require [circle.ruby :as ruby])
  (:use [clojure.tools.logging :only (info)])
  (:use [circle.util.straight-jacket])
  (:require [clipchat.rooms])
  (:require [circle.model.build :as build])
  (:require [circle.model.project :as project])
  (:import org.bson.types.ObjectId))


(defn send-email-build-notification [build]
  (ruby/require-rails)
  (ruby/ruby-require "simple_mailer")
  (ruby/send (ruby/get-class "SimpleMailer") :post_build_email_hook (-> @build :_id)))

(defn build-message [build]
  (ruby/require-rails)
  (let [build (ruby/send (ruby/get-class "Build") :find (-> @build :_id))]
    {:status (ruby/send build :status)
     :words (ruby/send build :status_in_words)
     :title (ruby/send build :status_as_title)
     :author (ruby/send build :committer_handle)}))

(defn send-hipchat-message [project message & args]
  (let [token (-> :hipchat_api_token project)
        room (-> :hipchat_room project)]
    (when (and token room)
      (clipchat.rooms/message
       token
       {:room-id room
        :from "Circle"
        :message (apply format message args)}))))

(defn send-hipchat-build-notification [build]
  (let [project (build/get-project @build)
        message (build-message build)]
    (send-hipchat-message project message)))

(defn send-hipchat-setup-notification [project-id]
  (let [project (project/get-by-id (ObjectId. project-id))]
    (send-hipchat-message project "Hipchat notifications enabled for %s" (-> :vcs_url project))))



(defn notify-build-results [build]
  (straight-jacket (send-email-build-notification build))
  (straight-jacket (send-hipchat-build-notification build)))

(defn send-build-error-email [build error]
  (straight-jacket
   (ruby/require-rails)
   (ruby/ruby-require "simple_mailer")
   (ruby/send (ruby/get-class "SimpleMailer") :build_error_email (-> @build :_id) error)))