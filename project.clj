(def jvm-opts (concat ["-Djava.net.preferIPv4Stack=true"
                       "-XX:MaxPermSize=256m"
                       "-XX:+UseConcMarkSweepGC"
                       "-Xss1m"
                       "-Xmx1024m"
                       "-XX:+CMSClassUnloadingEnabled"]
                      (when (not (or (= (System/getenv "USER") "pbiggar")
                                     (= (System/getenv "CIRCLE_DEBUG") "false")))
                        ["-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8030"])))

(defproject circle "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/clojure-contrib "1.3-b7125d79301cbc1ce44b24c5b29e57685202041a"]

                 ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
                 ;;; TECHNICAL_DEBT
                 ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

                 ;; Slingshot make a breaking change in version 0.8, where
                 ;; slingshot.core was renamed to slingshot.slingshot.
                 ;; Unfortunately, pallet and clj-ssh use version 0.2 and 0.5,
                 ;; but tentacles needs version 0.9. So we made our own which
                 ;; merged these.
                 [slingshot "0.9-80af18e4f9541c61ba5a0c62be28c4eb535bcd4c"]

                 ;; clj-json pulls in 1.5.0, which has a bug which prevents
                 ;; tentacles from working. Put this dep first
                 [org.codehaus.jackson/jackson-core-asl "1.8.5"]

                 [clj-stacktrace "0.2.4"]  ;; swank needs >= 0.2.4, but noir depends on 0.2.3, and maven is dumb.

                 ;; we added new fn, org/repos
                 [tentacles "0.1.3-461a6920fe3b3e7add0ef3b7fed6cba10bf3d114"]

                 [ring "1.0.2"] ;; clj-airbrake currently depends on 0.3.6, noir requires 1.0.x. need to Submit patch to clj-airbrake.
                 ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
                 ;;; Dependencies
                 ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


                 ;; Web
                 [noir "1.2.2"] ;; this is necessary because of the posterous template in src/circle/web/views/posterous

                 [clj-url "1.0.3"]
                 [clj-http "0.2.7"]
                 [clj-r53 "1.0.1"]
                 [commons-email "1.1"]
                 [cheshire "2.1.0"]
                 [com.amazonaws/aws-java-sdk "1.2.7"]

                 ;; DB
                 [c3p0 "0.9.1.2"]
                 [congomongo "0.1.7"]

                 ;; Queue
                 [resque-clojure "0.2.2"]

                 ;; Hooks
                 [clipchat/clipchat "1.0.0-SNAPSHOT"]

                 ;; Logging
                 [log4j "1.2.16"]
                 [log4j/apache-log4j-extras "1.1"]
                 [org.slf4j/slf4j-api "1.6.2"]
                 [org.slf4j/slf4j-log4j12 "1.6.2"]
                 [org.clojure/tools.logging "0.2.3"]
                 [org.log4mongo/log4mongo-java "0.7.0"]
                 [clj-airbrake "0.1.5-3d5b4bd54f1dc4d287845265417528f287c6d7f1"]
                 [clj-growl "0.2.1"]

                 [vmfest "0.2.3"]

                 ;; Misc
                 [swank-clojure "1.3.4"]

                 ;; Hooks

                 [commons-codec "1.4"]
                 [org.clojure/core.cache "0.5.0"]
                 [robert/hooke "1.1.2"]
                 [org.apache.commons/commons-compress "1.0"]
                 [org.xeustechnologies/jtar "1.0.4"]
                 [arohner-utils "0.0.3"]
                 [clj-yaml "0.3.1"]
                 [org.yaml/snakeyaml "1.9"] ;; clj-yaml 0.3.1 depends on snakeyaml 1.5, but jruby requires a later version
                 [fs "0.9.0"]
                 [clj-time "0.3.4"]
                 [doric "0.5.0"]
                 [robert/bruce "0.7.1"]
                 [com.jcraft/jsch  "0.1.45"] ; try to fix "Packet corrupt" errors.

                 ;; Pallet/jClouds
                 [org.cloudhoist/pallet "0.6.5"]
                 [org.cloudhoist/stevedore "0.7.1-SNAPSHOT"]
                 [org.jclouds/jclouds-core "1.2.1"]
                 [org.jclouds/jclouds-compute "1.2.1"]
                 [org.jclouds/jclouds-blobstore "1.2.1"]
                 [org.jclouds/jclouds-loadbalancer "1.2.1"]
                 [org.jclouds.driver/jclouds-log4j "1.2.1"]
                 [org.jclouds.driver/jclouds-jsch "1.2.1"]
                 [org.jclouds.provider/aws-ec2 "1.2.1"]

                 ;; Pallet Crates
                 [org.cloudhoist/automated-admin-user "0.6.0"]
                 [org.cloudhoist/git "0.5.0"]
                 [org.cloudhoist/postgres "0.6.1"]
                 [org.cloudhoist/rubygems "0.6.0"]
                 [org.cloudhoist/java "0.5.1"]
                 [org.cloudhoist/node-js "0.6.0-251878762734e6ffecdbdfe8e5c705869aa26b0b"]
                 [org.cloudhoist/nginx "f48febc9e5e43148719a11c3aeca4e6b668993e"]
                 [vmfest "0.2.3-a03a0006b5ac05018323fc6f4ccde0bdbdb45d9d"]
                 [lein-crate "0.1.0"]
                 [pallet-rvm "0.1-46e3991113e82b9d857bce7945b606423ba17699"]

                 ;; DO NOT REMOVE. I know it looks like we have midje
                 ;; down below, but we need it in both places. In
                 ;; production, trinidad doesn't load the dev
                 ;; dependencies (/lib/dev), and trinidad is a
                 ;; passive-aggressive bitch if any exceptions are
                 ;; thrown at startup.
                 [midje "1.3.1"]
                 [cdt "1.2.6.1-SNAPSHOT"]
                 [org.jruby.ext.posix/jnr-posix "1.1.8"]]

  :repositories {"circle-artifacts" "http://artifacts.circleci.com:8081/nexus/content/groups/all-repos/"}
  :library-path "jars"

  ;; https://github.com/technomancy/leiningen/issues/89
  ;; can't set lib/dev path
  :omit-default-repositories true
  :dev-dependencies [[lein-test-out "0.1.1"]
                     [midje "1.3.1" :exclusions [org.clojure/clojure]]
                     [lein-midje "1.0.7"]
                     [clojure-source "1.2.1"]
                     [org.jruby/jruby "1.6.5.1"]]

  :main ^{:skip-aot true} circle.init ;; careful https://github.com/marick/Midje/issues/12
  :jvm-opts ~jvm-opts)
