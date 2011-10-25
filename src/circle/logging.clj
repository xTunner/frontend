(ns circle.logging
  (:import (org.apache.log4j Logger
			     BasicConfigurator
                             EnhancedPatternLayout
			     Level
                             ConsoleAppender
                             FileAppender
                             SimpleLayout)
	   (org.apache.log4j.spi RootLogger))
  (:import (org.apache.log4j.rolling TimeBasedRollingPolicy
                                     RollingFileAppender))
  (:import org.apache.commons.logging.LogFactory))

(defn set-level [logger level]
  (. (Logger/getLogger logger) (setLevel level)))

(def circle-layout (EnhancedPatternLayout. "%p [%d] %t - %c - %m%n"))

(defn init []
  (let [rolling-policy (doto (TimeBasedRollingPolicy.)
                         (.setActiveFileName  "circle.log" )
                         (.setFileNamePattern "circle-%d{yyyy-MM-dd}.log.gz")
                         (.activateOptions))
        rolling-log-appender (doto (RollingFileAppender.)
                               (.setRollingPolicy rolling-policy)
                               (.setLayout circle-layout)
                               (.activateOptions))]
    (doto (Logger/getRootLogger)
      (.removeAllAppenders)
      (.addAppender rolling-log-appender)
      (.addAppender (ConsoleAppender. circle-layout))))
  (. (Logger/getRootLogger) (setLevel Level/INFO))
  (set-level "jclouds.wire" Level/OFF)
  (set-level "jclouds.signature" Level/OFF)
  (set-level "jclouds.headers" Level/OFF)

  (set-level "jclouds.ssh" Level/DEBUG)
  (set-level "org.jclouds.rest.internal.AsyncRestClientProxy" Level/OFF)
  (set-level "org.jclouds.http.internal.JavaUrlHttpCommandExecutorService" Level/OFF)
  (set-level "org.mortbay.log" Level/INFO)

  (set-level "org.apache.http.wire" Level/OFF)
  (set-level "org.apache.http.headers" Level/OFF))


(defn add-file-appender [loggername filename]
  (.addAppender (Logger/getLogger loggername)
                (doto (FileAppender.)
                  (.setLayout circle-layout))))