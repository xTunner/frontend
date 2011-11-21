(ns circle.backend.build.email
  (:require [clojure.string :as str])
  (:use [clojure.tools.logging :only (infof)])
  (:use [clojure.core.incubator :only (-?>)])
  (:require [circle.backend.build :as build])
  (:require [circle.backend.email :as email]))

(defn email-subject [build]
  (if (build/successful? build)
    (format "Build %s Success" (build/build-name build))
    (format "Build %s FAIL" (build/build-name build))))

(defn success-email-body [build]
  (format "Build %s successful" (build/build-name build)))

(defn fail-email-body [build]
  (str "Build of " (-> @build :vcs-revision) " failed " (str/join "\n" (map #(str (:out %) (:err %)) (-> @build :action-results)))))

(defn email-body [build]
  (if (build/successful? build)
    (success-email-body build)
    (fail-email-body build)))

(defn send-build-email [build to]
  (email/send :to to
              :subject (email-subject build)
              :body (email-body build)))

(defn except-to-string [e]
  (str (.getMessage e) "\n"
       (-> e
           (.getStackTrace)
           (seq)
           (->>
            (map #(.toString %))
            (str/join "\n")))))

(defn send-build-error-email [build e]
  (email/send :to "arohner@gmail.com"
              :subject "Circle exception"
              :body (str @build "\n" (except-to-string e))))

(defn notify-build-results [build]
  (let [recipients (-> @build :notify-email)]
    (if (seq recipients)
      (doseq [e recipients]
        (send-build-email build e))
      (infof "build %s has no notify, not sending email" @build))))