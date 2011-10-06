(ns circle.web
  (:require [noir.server :as server])
  (:use [circle.env :only (env production?)]))

(server/load-views "src/circle/web/views/")

(defn init [& m]
  
  (let [mode (if production?
               :prod
               :dev)
        port (Integer/parseInt (get (System/getenv) "HTTP_PORT" (if production?
                                                                  "80"
                                                                  "8080")))]
    (def server (server/start port {:mode mode
                                    :ns 'circle}))
    (println "web/init done")))