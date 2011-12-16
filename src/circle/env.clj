(ns circle.env
  (:require midje.semi-sweet)
  (:require clojure.test)
  (:use [circle.sh :only (sh)])
  (:require [clojure.string :as str])
  (:use [clojure.contrib.except :only (throw-if-not)]))

(def env (or (keyword (System/getenv "RAILS_ENV")) :development))

(defn production? []
  (= env :production))
(defn staging? []
  (= env :staging))
(defn test? []
  (= env :test))
(defn development? []
  (= env :development))

(defn validate-env []
  (throw-if-not (= 1 (count (filter true? [(production?) (staging?) (test?) (development?)]))) "Unexpected environment: %s" (str env)))

(validate-env)

(defn set-env
  "Takes a string, a rails environment setting"
  [rails-env]
  (alter-var-root (var env) (constantly (keyword rails-env)))
  (validate-env))

(when (or (production?) (staging?))
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