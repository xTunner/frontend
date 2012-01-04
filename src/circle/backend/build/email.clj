(ns circle.backend.build.email
  (:require [circle.ruby :as ruby])
  (:use [clojure.tools.logging :only (info)]))

(defn notify-build-results [build]
  (let [code (format
              "require 'simple_mailer'
SimpleMailer.build_email('%s')" (-> @build :_id))]
    (circle.ruby/eval code)))


(defn send-build-error-email [build error]
  (ruby/require-rails)
  (ruby/ruby-require "simple_mailer")
  (ruby/send (ruby/get-class "SimpleMailer") :build_error_email (-> @build :_id) error))