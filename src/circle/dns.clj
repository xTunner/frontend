(ns circle.dns
  (:use [doric.core :only (table)])
  (:require [clj-r53.client :as r53]))

(def account circle.aws-credentials/AWS-access-credentials)
(def zone-id (get (r53/list-hosted-zones account) "circleci.com."))

(defn print-records []
  (let [rows (->
              (clj-r53.client/list-resource-record-sets account zone-id)
              :rows)]
    (println (table [:name :type :value :ttl]
                    rows))))

(def with-r53-transaction (partial r53/with-r53-transaction account zone-id))

(def update-record (partial r53/update-record account zone-id))

(defn create-record [])
(defn update-staging
  "Sets staging.circle.com to the given ip address "
  [ip]
  (update-record {:name "staging.circleci.com."} {:value ip}))