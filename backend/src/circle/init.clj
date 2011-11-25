(ns circle.init
  (:require circle.env) ;; env needs to be loaded before any circle source files containing tests 
  (:require circle.swank)
  (:require circle.db)
  (:require circle.web)
  (:require circle.repl)
  (:require circle.logging))

(def init*
  (delay
   (try
     (circle.logging/init)
     (circle.swank/init)
     (circle.db/init)
     (circle.web/init)
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