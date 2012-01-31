(ns circle.backend.action.test-bash
  (:use midje.sweet)
  (:require [circle.backend.action.bash :as bash])
  (:require [circle.sh :as sh])
  (:require [circle.model.build :as build])
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.build.test-utils :as test])
  (:require [clj-time.core :as time])
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
  (let [build (test/minimal-build :actions [(bash/bash "hostname")])]
    (let [result (run/run-build build)]
      (-> @build :action-results (count)) => 1
      (-> @build :action-results (first) :exit_code) => 0
      (-> @build :action-results (first) :out (first) :message) => (-> (test/localhost-name) :out))))

;; first, test that timeouts don't cause things to break
(fact "timeouts don't cause bash to break"
  ;; normal build, with timeouts that should all pass
  (let [build (test/minimal-build :actions [(bash/bash (sh/q (sleep 1)
                                                             (echo "hello"))
                                                       :relative-timeout (time/secs 20)
                                                       :absolute-timeout (time/hours 1))]
                                  :node (test/localhost-ssh-map))
        result (run/run-build build)]
    (build/successful? build) => true))

(fact "relative timeout kills builds"
  ;; create a build with a long running action, and a short
  ;; timeout. Assert that the build fails, and it takes less time than
  ;; waiting for the action
  (let [start-time (time/now)
        build (test/minimal-build :actions [(bash/bash (sh/q (echo "hello")
                                                             (sleep 30))
                                                       :relative-timeout (time/secs 2))]
                                  :node (test/localhost-ssh-map))
        result (run/run-build build)]
    (build/successful? build) => false
    (-> @build :timedout) => true
    (< (time/in-secs (time/interval start-time (time/now))) 30) => true))

(fact "relative timeout doesn't kill builds that produce output"
  (let [start-time (time/now)
        build (test/minimal-build :actions [(bash/bash (sh/q (echo ".")
                                                             (sleep 1)
                                                             (echo ".")
                                                             (sleep 1)
                                                             (echo ".")
                                                             (sleep 1)
                                                             (echo ".")
                                                             (sleep 1)
                                                             (echo "."))
                                                       :relative-timeout (time/secs 2))]
                                  :node (test/localhost-ssh-map))
        result (run/run-build build)]
    (build/successful? build) => true))

(fact "absolute timeout kills builds"
  (let [start-time (time/now)
        build (test/minimal-build :actions [(bash/bash (sh/q (echo ".")
                                                             (sleep 1)
                                                             (echo ".")
                                                             (sleep 1)
                                                             (echo ".")
                                                             (sleep 1)
                                                             (echo ".")
                                                             (sleep 1)
                                                             (echo ".")
                                                             (sleep 1)
                                                             (echo ".")
                                                             (sleep 1)
                                                             (echo ".")
                                                             (sleep 1)
                                                             (echo ".")
                                                             (sleep 1))
                                                       :absolute-timeout (time/secs 2))]
                                  :node (test/localhost-ssh-map))
        result (run/run-build build)
        _ (def b build)]
    (build/successful? build) => false
    (-> @build :timedout) => true
    (< (time/in-secs (time/interval start-time (time/now))) 8) => true))

(fact "bash action passes env"
  (let [build (test/minimal-build :actions [(bash/bash (sh/q (echo "$FOO")) :environment {"FOO" "bar"})])]
    (let [result (run/run-build build)]
      (-> @build :action-results (first) :out (first) :message) => "bar\n")))

(fact "action name works"
  (bash/action-name (sh/q (rspec spec))) => "rspec spec")