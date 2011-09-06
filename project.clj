(defproject circleci "0.1.0-SNAPSHOT"
            :description "FIXME: write this!"
            :dependencies [[org.clojure/clojure "1.2.1"]
                           [noir "1.1.0"]
                           [clj-table "0.1.5"]
                           [clj-url "1.0.2"]
                           [c3p0 "0.9.1.2"]
                           [postgresql/postgresql "8.4-702.jdbc4"]
                           [swank-clojure "1.3.2"]
                           [log4j "1.2.14"]
                           [log4j/apache-log4j-extras "1.1"]
                           [org.clojure/tools.logging "0.2.0"]]
            :dev-dependencies []
            :main circleci.init)

