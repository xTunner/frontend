(ns circle.swank
  (:require swank.swank))

(defn init []
  (binding [*print-length* 100
            *print-level* 20]
  (println "Starting Swank")
  (clojure.main/with-bindings
    (swank.swank/start-server :port 4005
                              :encoding "utf-8"))))
