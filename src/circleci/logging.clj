(ns circleci.logging)

(defn init []
  (org.apache.log4j.BasicConfigurator/configure))