(ns circle.api.client.system
  (:use [clojure.tools.logging :only (infof)])
  (:require [clj-http.client :as http]))

(def auth ["bot@circleci.com" "brick amount must thirty"])

(defn api-call [{:keys [ip-addr path method protocol]
                   :or {protocol "https"}}]
  (http/request
   {:url (format "%s://%s/%s" protocol ip-addr path)
    :method method
    :insecure? true ;; ignore SSL warnings, in case this is a staging server
    :basic-auth auth}))

(defn graceful-shutdown [ip-addr & {:keys [protocol] :as options}]
  (infof "asking %s to shutdown gracefully" ip-addr)
  (let [resp (api-call (merge options {:ip-addr ip-addr
                                       :path "api/system/shutdown"
                                       :method :post}))]
    (infof "graceful shutdown: got resp %s" resp)))

(defn db-schema-version [ip-addr & {:keys [protocol] :as options}]
  (let [resp (api-call (merge options {:method :get
                                       :path "api/system/db_schema_version"
                                       :ip-addr ip-addr
                                       :options options}))]
    (when (= 200 (-> resp :status))
      (Integer/parseInt (-> resp :body)))))

(defn code-schema-version [ip-addr & {:keys [protocol] :as options}]
  (let [resp (api-call (merge options {:method :get
                                       :path "api/system/code_schema_version"
                                       :ip-addr ip-addr
                                       :options options}))]
    (when (= 200 (-> resp :status))
      (Integer/parseInt (-> resp :body)))))

(defn ping [ip-addr & {:keys [protocol] :as options}]
  (api-call (merge options {:method :get
                            :path "api/system/ping"
                            :ip-addr ip-addr})))