(ns circle.backend.action.test-bash
  (:use midje.sweet)
  (:require [circle.backend.action.bash :as bash])
  (:require [circle.sh :as sh])
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.build.test-utils :as test])
  (:require circle.db)
  (:use [circle.util.except :only (eat)]))

(circle.db/init)

(fact "remote-bash works"
  (let [resp (bash/remote-bash (test/localhost-ssh-map) "hostname")]
    resp => map?
    resp => (test/localhost-name)))

(fact "remote-bash works with quoted forms"
  (let [foo "foo"
        bar "bar"
        resp (bash/remote-bash (test/localhost-ssh-map) (sh/q (echo ~foo ~bar)))]
    resp => (clojure.java.shell/sh "echo" "foo" "bar")))

(fact "remote-bash works with pwd"
  (let [resp (bash/remote-bash (test/localhost-ssh-map) (sh/q (stat "zero")) :pwd "/dev")]
    (-> resp :exit) => 0))

(fact "bash actions are named after their commands"
  (let [build (test/minimal-build :actions [(bash/bash "hostname")])]
    (-> @build :actions (first) :name) => "hostname"))

(fact "bash action adds action results"
  (let [_ (test/clean-test-project)
        build (test/minimal-build :actions [(bash/bash "hostname")])]
    (let [result (run/run-build build)]
      (-> @build :action-results (count)) => 1
      (-> @build :action-results (first) :exit_code) => 0
      (-> @build :action-results (first) :out (first) :message) => (-> (test/localhost-name) :out))))

(fact "bash action passes env"
  (let [build (test/minimal-build :actions [(bash/bash (sh/q (echo "$FOO")) :environment {"FOO" "bar"})])]
    (let [result (run/run-build build)]
      (-> @build :action-results (first) :out (first) :message) => "bar\n")))

(fact "action name works"
  (bash/action-name (sh/q (rspec spec))) => "rspec spec")