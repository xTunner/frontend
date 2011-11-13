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

(defn last-local-commit []
  (->
   (sh "git" "log" "-1" "--pretty=format:%H")
   :out))

(defn last-remote-commit []
  (->
   (sh "git" "log" "-1" "--pretty=format:%H" "origin/master")
   :out))