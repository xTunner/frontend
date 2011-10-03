(ns circleci.init
  ;; (:require circleci.swank)
  (:require circleci.db)
  (:require circleci.db.migrations)
  (:require circleci.web)
  (:require circleci.repl)
  (:require circleci.logging)
  ;; (:require circleci.backend.nodes
  ;;           circleci.backend.project.rails
  ;;           circleci.backend.project.circleci)
  )

(def init*
  (delay
   (try
     (circleci.logging/init)
     ;; workaround for Heroku not liking us starting up swank
     (when (System/getenv "SWANK")
       (require 'circleci.swank)
       (.invoke (ns-resolve 'circleci.swank 'init)))
     (circleci.db/init)
     (circleci.db.migrations/init)
     (circleci.web/init)
     (circleci.repl/init)
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