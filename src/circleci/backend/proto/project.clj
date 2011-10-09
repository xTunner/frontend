(defproject proto "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [clj-yaml "0.3.0-SNAPSHOT"]
                 [org.xeustechnologies/jtar "1.0.4"] ; work with tar files (Java)
                 ]
  
  :repositories {"sonatype-releases" "http://oss.sonatype.org/content/repositories/releases"
                 "sonatype-snapshots" "http://oss.sonatype.org/content/repositories/snapshots"}
  :dev-dependencies [[swank-clojure "1.3.2"]]
  :main proto.main)
