(ns circle.api.server
  (:use noir.core)
  (:require [noir.response :as response])
  (:require [noir.server :as server])

  (:use [clojure.tools.logging :only (infof)])
  (:require [cheshire.core :as cheshire])
  (:use [circle.util.core :only (defn-once)]))

(defn logging [handler]
  (fn [request]
    (infof "request: %s" request)
    (let [resp (handler request)]
      (infof "resp: %s" resp)
      resp)))

(defn-once init
  (server/add-middleware (var logging))
  (def server (server/start 8080)))

(defn stop []
  (server/stop server))

(defpage "/api/v1/hello" []
  (response/json {:message "hello"}))

(defpage "/whoami" []
  ; get the user from the cookie, return the users details
  )
