(defproject circleci "0.1.0-SNAPSHOT"
            :description "FIXME: write this!"
            :dependencies [[org.clojure/clojure "1.2.1"]
                           [clj-json "0.4.0"] ;; noir pulls in clj-json 0.3.2 which isn't compatible w/ clojure 1.3. Put this dep ahead to pull it in first.
                           [noir "1.1.1-SNAPSHOT"]
                           [clj-table "0.1.5"]
                           [clj-url "1.0.2"]
                           [c3p0 "0.9.1.2"]
                           [swank-clojure "1.3.2"]
                           [log4j "1.2.14"]
                           [log4j/apache-log4j-extras "1.1"]
                           [clj-http "0.1.3"]

                           [commons-codec "1.4"]
                           [arohner-utils "0.0.2"]
                           [clj-yaml "0.3.1"]
                           [org.danlarkin/clojure-json "1.2-SNAPSHOT"]
                           [org.cloudhoist/pallet "0.6.4"]
                           [org.jclouds/jclouds-all "1.0.0"]
                           [org.jclouds.driver/jclouds-log4j "1.0.0"]
                           [org.jclouds.driver/jclouds-jsch "1.0.0"]
                           
                           ;; Pallet Crates
                           [org.cloudhoist/automated-admin-user "0.6.0"]
                           [org.cloudhoist/git "0.5.0"]
                           [org.cloudhoist/postgres "0.6.1"]
                           [org.cloudhoist/rubygems "0.6.0"]
                           [pallet-rvm "0.1"]]
            :repositories {"sonatype-releases" "http://oss.sonatype.org/content/repositories/releases"
                           "sonatype-snapshots" "http://oss.sonatype.org/content/repositories/snapshots"}
            :dev-dependencies [[lein-test-out "0.1.1"]
                               [midje "1.2.0"]]
            :main circleci.init)

