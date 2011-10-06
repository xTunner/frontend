(ns circle.web
  (:require [noir.server :as server]))

(server/load-views "src/circle/web/views/")

(defn init [& m]
  (let [production? (= (System/getenv "CIRCLE_ENV") "production")
        mode (if production?
               :prod
               :dev)
        port (Integer/parseInt (get (System/getenv) "HTTP_PORT" (if production?
                                                                  "8080"
                                                                  "80")))]
    (def server (server/start port {:mode mode
                                    :ns 'circle}))
    (println "web/init done")))