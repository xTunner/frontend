(ns circle.init
  (:require circle.env) ;; env needs to be loaded before any circle source files containing tests
  (:require circle.swank)
  (:require circle.db)
  (:require circle.repl)
  (:require circle.logging)
  (:require circle.backend.build.run)
  (:require circle.backend.build.config)
  (:require fs))

(defonce init*
  (delay
   (try
     (println "circle.init/init")
     (circle.logging/init)
     (when (= "true" (System/getenv "CIRCLE_SWANK"))
       (circle.swank/init))
     (circle.db/init)
     (circle.repl/init)
     (println (java.util.Date.))
     true
     (catch Exception e
       (println "caught exception on startup:")
       (.printStackTrace e)
       (println "exiting")
       (System/exit 1)))))

(defn init
  "Start everything up. idempotent."
  []
  @init*)

(defn -main []
  (init))