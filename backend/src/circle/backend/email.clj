(ns circle.backend.email
  (:use [circle.util.except :only (throw-if-not)])
  (:use [circle.util.args :only (require-args)])
  (:refer-clojure :exclude [send])
  (:import (org.apache.commons.mail SimpleEmail
	                            DefaultAuthenticator)))

(def account-name "builds@circleci.com")
(def friendly-name "Circle Builds")
(def password "powerful development did finally")
(def smtp-server "smtp.gmail.com")

(defn send 
  ; translated from
  ; http://forums.sun.com/thread.jspa?threadID=5351826, because
  ; apparently the naive way doesn't work.
  [& {:keys [to subject body] :as args}]
  (println "email/send:" args)
  (require-args to subject body)
  (throw-if-not (> (count body) 1) "body must have at least one char")
  (let [email (new SimpleEmail)]
    (doto email
;      (.setDebug true)
      (.setHostName smtp-server)
      (.setFrom account-name, friendly-name)
      (.addTo to)
      (.setSubject subject)
      (.setMsg body)
      (.setSSL true)
      (.setTLS true)
      (.setSmtpPort 465)
      (.setAuthenticator (new DefaultAuthenticator account-name password)))
    (doto (.getProperties (.getMailSession email))
      (.put "mail.smtp.auth" "true")
      ;(.put "mail.debug" "true")
      (.put "mail.smtp.port" "465")
      (.put "mail.smtp.socketFactory.port" "465")
      (.put "mail.smtp.socketFactory.class" "javax.net.ssl.SSLSocketFactory")
      (.put "mail.smtp.socketFactory.fallback" "false")
      (.put "mail.smtp.starttls.enable" "true"))
    (future (.send email))))