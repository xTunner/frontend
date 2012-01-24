(ns circle.backend.build.email
  (:require [circle.ruby :as ruby])
  (:use [clojure.tools.logging :only (info)])
  (:use [circle.util.straight-jacket]))

(defn notify-build-results* [build]
  (ruby/require-rails)
  (ruby/ruby-require "simple_mailer")
  (ruby/send (ruby/get-class "SimpleMailer") :build_email (-> @build :_id)))

(defn notify-build-results [build]
  (straight-jacket
   (notify-build-results* build)))

(defn send-build-error-email [build error]
  (straight-jacket
   (ruby/require-rails)
   (ruby/ruby-require "simple_mailer")
   (ruby/send (ruby/get-class "SimpleMailer") :build_error_email (-> @build :_id) error)))