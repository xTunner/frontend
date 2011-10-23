(ns circle.init
  ;; (:require circle.swank)
  (:require circle.db)
  (:require circle.web)
  (:require circle.repl)
  (:require circle.logging)
  ;; (:require circle.backend.nodes
  ;;           circle.backend.project.rails
  ;;           circle.backend.project.circle)
  )

(def init*
  (delay
   (try
     (circle.logging/init)
     ;; workaround for Heroku not liking us starting up swank
     (when (System/getenv "SWANK")
       (require 'circle.swank)
       (.invoke (ns-resolve 'circle.swank 'init)))
     (circle.db/init)
     (circle.web/init)
     (circle.repl/init)
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