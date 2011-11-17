(ns circle.env
  (:use [clojure.java.shell :only (sh)])
  (:use [clojure.contrib.except :only (throw-if-not)]))

(def env (condp = (System/getenv "CIRCLE_ENV")
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

(defn last-local-commit []
  (->
   (sh "git" "log" "-1" "--pretty=format:%H")
   :out))

(defn last-remote-commit []
  (->
   (sh "git" "log" "-1" "--pretty=format:%H" "origin/master")
   :out))