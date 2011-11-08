(ns circle.backend.action.test-bash
  (:use midje.sweet)
  (:use [circle.backend.build :only (*pwd* *env*)])
  (:require [circle.backend.action.bash :as bash]))

(fact "format-bash-command handles pwd"
  (binding [*pwd* "/home/test"]
    (bash/emit-form "hostname") => "cd /home/test\nhostname\n"))

(fact "format-bash-command handles env"
  (binding [*env* {"CIRCLE_ENV" :production
                   "SWANK" true}]
    (bash/emit-form "lein run") => "export CIRCLE_ENV=production\nexport SWANK=true\nlein run\n"))

(fact "format-bash-command handles env and pwd"
  (binding [*pwd* "/home/test"
            *env* {"CIRCLE_ENV" :production
                   "SWANK" true}]
    (bash/emit-form "lein run") => "cd /home/test\nexport CIRCLE_ENV=production\nexport SWANK=true\nlein run\n"))

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

