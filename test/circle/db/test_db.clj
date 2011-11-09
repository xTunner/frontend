(ns circle.db.test-db
  (:use midje.sweet)
  (:require [somnium.congomongo :as mongo])
  (:use [clojure.tools.logging :only (infof)])
  (:require [clj-time.core :as time])
  (:require [circle.db])
  (:require [circle.logging]))

(circle.db/init)
(circle.logging/init)

(fact "logging uses the same DB as congomongo uses"
  (let [message (str (gensym "logging-test"))
        fetcher #(mongo/fetch-count :logs
                                  :where {:message message
                                          :level "INFO"})
        log-entries-before (fetcher)
        _ (infof message)
        log-entries-after (fetcher)]
    log-entries-after => (+ 1 log-entries-before)))