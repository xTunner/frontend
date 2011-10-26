(ns circle.env
  (:use [clojure.java.shell :only (sh)]))

(def env (if (= (System/getenv "CIRCLE_ENV") "production")
           :production
           :test))

(def production? (= env :production))

(defn last-local-commit []
  (->
   (sh "git" "log" "-1" "--pretty=format:%H")
   :out))

(defn last-remote-commit []
  (->
   (sh "git" "log" "-1" "--pretty=format:%H" "origin/master")
   :out))