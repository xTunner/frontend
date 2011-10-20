(ns circle.backend.build.email
  (:require [circle.backend.build :as build])
  (:require [circle.backend.email :as email])
  (:require [clojure.string :as str]))

(defn email-subject [build-result]
  (if (build/successful? build-result)
    "Build Success"
    "FAIL"))

(defn success-email [build]
  (str "Build of " (-> build :vcs-revision) "successful"))

(defn fail-email [build]
  (str "Build of " (-> build :vcs-revision) " failed " (str/join "\n" (map #(str (:out %) (:err %)) (-> build :action-results)))))

(defn email-body [build]
  (if (build/successful? build)
    (success-email build)
    (fail-email build)))

(defn send-build-email [build]
  (email/send :to (-> build :notify-email)
              :subject (email-subject build)
              :body (email-body build)))

(defn send-build-error-email [build e]
  (email/send :to "arohner@gmail.com"
              :subject "Circle exception"
              :body (with-out-str (str build) "\n" (.printStackTrace e))))
