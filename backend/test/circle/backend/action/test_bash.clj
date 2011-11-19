(ns circle.backend.action.test-bash
  (:use midje.sweet)
  (:require [circle.backend.action.bash :as bash])
  (:require [circle.sh :as sh])
  (:require [circle.backend.build.run :as run])
  (:require circle.db)
  (:use [circle.backend.build.test-utils :only (minimal-build)])
  (:use [circle.util.except :only (eat)]))

(circle.db/init)

(defn localhost-ssh-map []
  (let [username (System/getenv "USER")
        ssh-dir (format "%s/.ssh" (System/getProperty "user.home"))]
    (assert username)
    {:username username
     :ip-addr "localhost"
     :public-key (or (eat (slurp (format "%s/id_rsa.pub" ssh-dir)))
                     (eat (slurp (format "%s/id_dsa.pub" ssh-dir))))
     :private-key (or (eat (slurp (format "%s/id_rsa" ssh-dir)))
                      (eat (slurp (format "%s/id_dsa" ssh-dir))))}))

(defn localhost-name []
  (clojure.java.shell/sh "hostname"))

(fact "remote-bash works"
  (let [resp (bash/remote-bash (localhost-ssh-map) "hostname")]
    resp => map?
    resp => (localhost-name)))

(fact "remote-bash works with quoted forms"
  (let [foo "foo"
        bar "bar"
        resp (bash/remote-bash (localhost-ssh-map) (sh/quasiquote (echo ~foo ~bar)))]
    resp => (clojure.java.shell/sh "echo" "foo" "bar")))

(fact "remote-bash works with pwd"
  (let [resp (bash/remote-bash (localhost-ssh-map) (sh/quasiquote (stat "zero")) :pwd "/dev")]
    (-> resp :exit) => 0))

(fact "bash actions are named after their commands"
  (let [build (minimal-build :actions [(bash/bash "hostname")])]
    (-> @build :actions (first) :name) => "hostname"))

(fact "bash action works"
  (let [build (minimal-build :actions [(bash/bash "hostname")])]
    (binding [bash/ssh-map-for-build (fn [build] (localhost-ssh-map))]
      (let [result (run/run-build build)]
        (-> @build :action-results (count)) => 1
        (-> @build :action-results (first) :exit-code) => 0
        (-> @build :action-results (first) :out (first) :message) => (-> (localhost-name) :out)))))

(fact "bash action passes env"
  (let [build (minimal-build :actions [(bash/bash (sh/quasiquote (echo "$FOO")) :environment {"FOO" "bar"})])]
    (binding [bash/ssh-map-for-build (fn [build] (localhost-ssh-map))]
      (let [result (run/run-build build)]
        (-> @build :action-results (first) :out (first) :message) => "bar\n"))))