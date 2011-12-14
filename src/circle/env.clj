(ns circle.env
  (:require midje.semi-sweet)
  (:require clojure.test)
  (:use [circle.sh :only (sh)])
  (:require [clojure.string :as str])
  (:use [clojure.contrib.except :only (throw-if-not)]))

(def env (condp = (System/getenv "RAILS_ENV")
           "production" :production
           "staging" :staging
           nil :local))

(def production? (= env :production))
(def staging? (= env :staging))
(def local? (= env :local))
(throw-if-not (= 1 (count (filter true? [production? staging? local?]))))

(when production?
  (alter-var-root (var midje.semi-sweet/*include-midje-checks*) (constantly false))
  (alter-var-root (var clojure.test/*load-tests*) (constantly false)))

(defn username
  "Returns the username that started this JVM"
  []
  (-> (sh "whoami")
      :out
      (str/trim)))

(defn hostname
  "hostname of the box"
  []
  (-> (sh "hostname")
      :out
      (str/trim)))