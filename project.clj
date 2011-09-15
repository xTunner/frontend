(defproject circleci "0.1.0-SNAPSHOT"
            :description "FIXME: write this!"
            :dependencies [[org.clojure/clojure "1.2.1"]
                           [noir "1.1.1-SNAPSHOT"]
                           [clj-table "0.1.5"]
                           [clj-url "1.0.2"]
                           [c3p0 "0.9.1.2"]
                           [swank-clojure "1.3.2"]
                           [log4j "1.2.14"]
                           [log4j/apache-log4j-extras "1.1"]
                           [commons-codec "1.4"]
                           [org.cloudhoist/pallet "0.6.4"]
                           [org.cloudhoist/pallet-crates-all "0.5.1-SNAPSHOT"]
                           [org.jclouds/jclouds-all "1.0.0"]
                           [org.jclouds.driver/jclouds-log4j "1.0.0"]
                           [org.jclouds.driver/jclouds-jsch "1.0.0"]]
            :repositories {"sonatype-releases" "http://oss.sonatype.org/content/repositories/releases"
                           "sonatype-snapshots" "http://oss.sonatype.org/content/repositories/snapshots"}
            :dev-dependencies []
            :main circleci.init)

