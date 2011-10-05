(ns circle.logging
  (:import (org.apache.log4j Logger
			     BasicConfigurator
                             EnhancedPatternLayout
			     Level
                             ConsoleAppender
                             SimpleLayout)
	   (org.apache.log4j.spi RootLogger))
  (:import (org.apache.log4j.rolling TimeBasedRollingPolicy
                                     RollingFileAppender))
  (:import org.apache.commons.logging.LogFactory))

(defn init []
  (let [rolling-policy (doto (TimeBasedRollingPolicy.)
                         (.setActiveFileName  "circle.log" )
                         (.setFileNamePattern "circle-%d{yyyy-MM-dd}.log.gz")
                         (.activateOptions))
        layout (EnhancedPatternLayout. "%p [%d] %t - %c - %m%n")
        rolling-log-appender (doto (RollingFileAppender.)
                               (.setRollingPolicy rolling-policy)
                               (.setLayout layout)
                               (.activateOptions))]
    (doto (Logger/getRootLogger)
      (.removeAllAppenders)
      (.addAppender rolling-log-appender)
      (.addAppender (ConsoleAppender. layout))))
  (. (Logger/getRootLogger) (setLevel Level/DEBUG)))
