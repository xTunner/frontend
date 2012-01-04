(ns circle.swank
  (:require swank.swank)
  (:require [circle.env :as env]))

(defn port []
  (cond
   (System/getenv "SWANK_PORT") (Integer/parseInt (System/getenv "SWANK_PORT"))
   (env/test?) 4006
   :else 4005))

(defn init []
  (when (= "true" (System/getenv "CIRCLE_SWANK"))
    (binding [*print-length* 100
              *print-level* 20]
      (println "Starting Swank")
      (clojure.main/with-bindings
        (swank.swank/start-server :port (port)
                                  :encoding "utf-8")))))
