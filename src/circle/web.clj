(ns circle.web
  (:require [noir.server :as server]))

(server/load-views "src/circle/web/views/")

(defn init [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer/parseInt (get (System/getenv) "PORT" "8080"))]
    (def server (server/start port {:mode mode
                                    :ns 'circle}))
    (println "web/init done")))