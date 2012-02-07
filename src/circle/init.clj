(ns circle.init
  (:require circle.env) ;; env needs to be loaded before any circle source files containing tests
  (:use [circle.util.core :only (defn-once)])
  (:require circle.swank)
  (:require circle.db)
  (:require circle.repl)
  (:require circle.redis)
  (:require circle.resque)
  (:require circle.logging)
  (:require circle.backend.build.run)
  (:require circle.backend.build.config)
  (:require circle.backend.build.resources)
  (:require fs))

(defn-once init
  (try
    (println "circle.init/init")
    (circle.logging/init)
    (circle.swank/init)
    (circle.repl/init)
    (circle.db/init)
    (circle.redis/init)
    (circle.resque/init)
    (circle.backend.build.resources/init)

    (println (java.util.Date.))
    true
    (catch Exception e
      (println "caught exception on startup:")
      (.printStackTrace e)
      (println "exiting")
      (System/exit 1))))

(defn -main []
  (init))