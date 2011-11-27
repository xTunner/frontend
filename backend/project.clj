(def jvm-opts (concat ["-Djava.net.preferIPv4Stack=true"
                       "-XX:MaxPermSize=256m"
                       "-XX:+UseConcMarkSweepGC"
                       "-XX:+CMSClassUnloadingEnabled"]
                      (when (not= (System/getenv "USER") "pbiggar")
                        ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8030"])))


(defproject circle "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [clj-json "0.4.0"] ;; noir pulls in clj-json 0.3.2 which isn't compatible w/ clojure 1.3. Put this dep ahead to pull it in first.

                 ;; Web
                 [noir "1.2.0"]
                 [clj-url "1.0.3"]
                 [clj-http "0.2.1"]
                 [clj-r53 "1.0.1"]
                 [commons-email "1.1"]
                 [org.danlarkin/clojure-json "1.2-SNAPSHOT"]
                 [com.amazonaws/aws-java-sdk "1.2.7"]

                 ;; DB
                 [c3p0 "0.9.1.2"]
                 [congomongo "0.1.7"]

                 ;; Logging
                 [log4j "1.2.16"]
                 [log4j/apache-log4j-extras "1.1"]
                 [org.slf4j/slf4j-api "1.6.2"]
                 [org.slf4j/slf4j-log4j12 "1.6.2"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.log4mongo/log4mongo-java "0.7.0"]

                 [vmfest "0.2.3"]

                 ;; Misc
                 [commons-codec "1.4"]
                 [org.apache.commons/commons-compress "1.0"]
                 [org.xeustechnologies/jtar "1.0.4"]
                 [arohner-utils "0.0.3"]
                 [clj-yaml "0.3.1"]
                 [fs "0.9.0"]
                 [clj-time "0.3.1"]
                 [doric "0.5.0"]
                 [robert/bruce "0.7.1"]
                 [com.jcraft/jsch  "0.1.45"] ; try to fix "Packet corrupt" errors.
                 [org.eclipse.jgit/org.eclipse.jgit "1.1.0.201109151100-r"]
                 [com.cemerick/pomegranate "0.0.1"]

                 ;; Pallet/jClouds
                 [org.cloudhoist/pallet "0.6.5"]
                 [org.jclouds/jclouds-core "1.2.1"]
                 [org.jclouds/jclouds-compute "1.2.1"]
                 [org.jclouds/jclouds-blobstore "1.2.1"]
                 [org.jclouds/jclouds-loadbalancer "1.2.1"]
                 [org.jclouds.driver/jclouds-log4j "1.2.1"]
                 [org.jclouds.driver/jclouds-jsch "1.2.1"]
                 [org.jclouds.provider/aws-ec2 "1.2.1"]

                 ;; Requires github.com/pbiggar/jnr-posix
                 [com.github.jnr/jnr-posix "2.1-5ad6cc85878cbaa4c698640a73578d8236119456"]
                 [com.github.jnr/jnr-ffi "0.6.0"]
                 [com.github.jnr/jnr-constants "0.8.2"]

                 ;; Pallet Crates
                 [org.cloudhoist/automated-admin-user "0.6.0"]
                 [org.cloudhoist/git "0.5.0"]
                 [org.cloudhoist/postgres "0.6.1"]
                 [org.cloudhoist/rubygems "0.6.0"]
                 [org.cloudhoist/java "0.5.1"]
                 [org.cloudhoist/nginx "f48febc9e5e43148719a11c3aeca4e6b668993e"]
                 [lein-crate "0.1.0"]
                 [lein-daemon "0.4.2"]
                 [pallet-rvm "0.1"]
                 [cdt "1.2.6.1-SNAPSHOT"]]

  :repositories {"circle-artifacts" "http://artifacts.circleci.com:8081/nexus/content/groups/all-repos/"}
  :omit-default-repositories true
  :dev-dependencies [[lein-test-out "0.1.1"]
                     [midje "1.2.0"]
                     [lein-midje "1.0.4"]
                     [lein-daemon "0.4.2"]
                     [clojure-source "1.2.1"]]
  :main ^{:skip-aot true} circle.init ;; careful https://github.com/marick/Midje/issues/12
  :jvm-opts ~jvm-opts)

