(ns circleci.server
  (:require swank.swank)
  (:require [noir.server :as server]))

(defn start-swank []
  (binding [*print-length* 100
            *print-level* 20]
  (println "Starting Swank")
  (clojure.main/with-bindings
    (swank.swank/start-server :port 4005
                              :encoding "utf-8"))))

(server/load-views "src/circleci/views/")


(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port 8080]
    (start-swank)
    (server/start port {:mode mode
                        :ns 'circleci})))