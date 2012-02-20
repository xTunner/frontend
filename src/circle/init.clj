(ns circle.init
  (:require circle.env) ;; env needs to be loaded before any circle source files containing tests
  (:use [circle.util.core :only (defn-once)])
  (:require circle.swank)
  (:require circle.db)
  (:require circle.repl)
  (:require circle.resque)
  (:require circle.logging)
  (:require circle.api.server)
  (:require circle.backend.build.run)
  (:require circle.backend.build.config)
  (:use [clojure.tools.logging :only (error)])
  (:require fs))

(defn-once init
  (try
    (println "circle.init/init")
    (circle.logging/init)
    (try
      (circle.swank/init)
      (catch Exception e
        (error e "error starting swank")))
    (circle.repl/init)
    (circle.db/init)
    (circle.api.server/init)
    (circle.resque/init)

    (println (java.util.Date.))
    true
    (catch Exception e
      (println "caught exception on startup:")
      (.printStackTrace e)
      (println "exiting")
      (System/exit 1))))

(defn -main []
  (init))