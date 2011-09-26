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
                           [commons-codec "1.4"]
                           [arohner-utils "0.0.2"]
                           [org.clojars.arohner/clj-yaml "fc59d5c4337614734bba24ea2ee90aa5237dde5b"]
                           [org.danlarkin/clojure-json "1.2-SNAPSHOT"]
                           [org.cloudhoist/pallet "0.6.4"]
                           [org.cloudhoist/pallet-crates-all "0.5.1-20110915.191835-56"]
                           [org.cloudhoist/tomcat "0.5.1-SNAPSHOT"] 
                           [org.cloudhoist/postgres "0.5.1-SNAPSHOT"] 
                           [pallet-rvm "0.1"]
                           [org.jclouds/jclouds-all "1.0.0"]
                           [org.jclouds.driver/jclouds-log4j "1.0.0"]
                           [org.jclouds.driver/jclouds-jsch "1.0.0"]]
            :repositories {"sonatype-releases" "http://oss.sonatype.org/content/repositories/releases"
                           "sonatype-snapshots" "http://oss.sonatype.org/content/repositories/snapshots"}
            :dev-dependencies []
            :main circleci.init)

