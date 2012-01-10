(ns circle.db.test-db
  (:use midje.sweet)
  (:require [somnium.congomongo :as mongo])
  (:use [clojure.tools.logging :only (infof)])
  (:require [clj-time.core :as time])
  (:require [circle.db])
  (:require [circle.logging]))
