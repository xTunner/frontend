(ns circleci.init
  (:require circleci.db)
  (:require circleci.db.migrations)
  (:require circleci.web)
  (:require circleci.repl)
  (:require circleci.logging))

(defn -main []
  (try
    (circleci.logging/init)
    (circleci.db/init)
    (circleci.db.migrations/init)
    (circleci.web/init)
    (circleci.repl/init)
    (catch Exception e
      (println "caught exception on startup:")
      (.printStackTrace e)
      (println "exiting")
      (System/exit 1))))