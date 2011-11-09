(ns circle.backend.action.test-bash
  (:use midje.sweet)
  (:use [circle.backend.build :only (*pwd* *env*)])
  (:require [circle.backend.action.bash :as bash])
  (:require [circle.backend.build.run :as run])
  (:require circle.db)
  (:use [circle.backend.build.utils :only (minimal-build)]))

(circle.db/init)

(fact "emit-form works"
  (bash/emit-form "hostname") => "hostname")

(fact "emit-form handles stevedore"
  (bash/emit-form (bash/quasiquote (hostname))) => "hostname")

(fact "format-bash-command handles pwd"
  (bash/emit-form "hostname" :pwd "/home/test") => "cd /home/test\nhostname\n")

(fact "format-bash-command handles env"
  (bash/emit-form "lein run" :environment {"CIRCLE_ENV" :production
                                           "SWANK" true})
  => "export CIRCLE_ENV=production\nexport SWANK=true\nlein run\n")

(fact "format-bash-command handles keywords in env"
  (bash/emit-form "lein run" :environment {:CIRCLE_ENV :production
                                           :SWANK true})
  => "export CIRCLE_ENV=production\nexport SWANK=true\nlein run\n")

(fact "format-bash-command handles env and pwd"
  (bash/emit-form "lein run"
                  :pwd "/home/test"
                  :environment {"CIRCLE_ENV" :production
                                "SWANK" true})
  => "cd /home/test\nexport CIRCLE_ENV=production\nexport SWANK=true\nlein run\n")

(fact "*pwd* is used"
  (binding [*pwd* "/home/test"]
    (bash/emit-form "lein run")
    => "cd /home/test\nlein run\n"))

(fact "explicit pwd overrides *pwd*"
  (binding [*pwd* "/home/test"]
    (bash/emit-form "lein run"
                    :pwd "/home/test/circle")
    => "cd /home/test/circle\nlein run\n"))


(defn localhost-ssh-map []
  (let [username (System/getenv "USER")
        ssh-dir (format "%s/.ssh" (System/getProperty "user.home"))]
    (assert username)
    {:username username
     :ip-addr "localhost"
     :public-key (slurp (format "%s/id_rsa.pub" ssh-dir))
     :private-key (slurp (format "%s/id_rsa" ssh-dir))}))

(defn localhost-name []
  (clojure.java.shell/sh "hostname"))

(fact "remote-bash works"
  (let [resp (bash/remote-bash (localhost-ssh-map) "hostname")]
    resp => map?
    resp => (localhost-name)))

(fact "remote-bash works with quoted forms"
  (let [foo "foo"
        bar "bar"
        resp (bash/remote-bash (localhost-ssh-map) (bash/quasiquote (echo ~foo ~bar)))]
    resp => (clojure.java.shell/sh "echo" "foo" "bar")))

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
  (let [build (minimal-build :actions [(bash/bash (bash/quasiquote (echo "$FOO")) :environment {"FOO" "bar"})])]
    (binding [bash/ssh-map-for-build (fn [build] (localhost-ssh-map))]
      (let [result (run/run-build build)]
        (-> @build :action-results (first) :out (first) :message) => "bar\n"))))

