(ns circle.http.server
  (:use [circle.util.core :only (defn-once)])
  (:use [clojure.tools.logging :only (infof)])

  (:use noir.core)
  (:require [noir.response :as response])
  (:require [noir.server :as server]))

(defn logging [handler]
  (fn [request]
    (infof "request: %s" request)
    (let [resp (handler request)]
      (infof "resp: %s" resp)
      resp)))


(defn port []
  (if (circle.env/test?)
    8081
    8080))


(defn-once init
  (server/add-middleware (var logging))
  (def server (server/start (port))))

(defn stop []
  (server/stop server))

(defpage "/" []
  (html
   [:html
    [:head
     [:script {:src "/assets/haml-coffee.min.js"}]
     [:script "var hamlc = require('haml-coffee');
               tmpl = hamlc.compile('alert(\"yes!\");');
               html = tmpl({});"]]]))
