(ns circleci.init
  ;;(:require circleci.swank)
  (:require circleci.db)
  (:require circleci.db.migrations)
  (:require circleci.web)
  (:require circleci.repl))

(defn -main []
  ;; (circleci.swank/init)
  (circleci.db/init)
  (circleci.db.migrations/init)
  (circleci.web/init)
  (circleci.repl/init))