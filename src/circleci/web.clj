(ns circleci.web
  (:require [noir.server :as server]))

(server/load-views "src/circleci/web/views/")

(defn init [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer/parseInt (get (System/getenv) "PORT" "8080"))]
    (def server (server/start port {:mode mode
                                    :ns 'circleci}))
    (println "web/init done")))