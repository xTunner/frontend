(ns circle.web
  (:require [noir.server :as server])
  (:use [circle.env :only (env production?)])
  (:require [circle.web.middleware.logging]))

(server/load-views "src/circle/web/views/")

(defn init [& m]
  (server/add-middleware circle.web.middleware.logging/wrap-log)
  
  (let [mode (if production?
               :prod
               :dev)
        port (Integer/parseInt (get (System/getenv) "HTTP_PORT" "8080"))]

    (def server (server/start port {:mode mode
                                    :ns 'circle}))
    (println "web/init done")))

(defn stop []
  (server/stop server))

(defn restart []
  (stop)
  (init))

