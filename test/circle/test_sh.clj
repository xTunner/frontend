(ns circle.test-sh
  (:require [circle.sh :as sh])
  (:use [midje.sweet]))

(fact "emit-form works"
  (sh/emit-form "hostname") => "hostname")

(fact "emit-form handles stevedore"
  (sh/emit-form (sh/quasiquote (hostname))) => "hostname")

(fact "format-bash-command handles pwd"
  (sh/emit-form "hostname" :pwd "/home/test") => "cd /home/test\nhostname\n")

(fact "format-bash-command handles env"
  (sh/emit-form "lein run" :environment {"CIRCLE_ENV" :production
                                           "SWANK" true})
  => "export CIRCLE_ENV=production\nexport SWANK=true\nlein run\n")

(fact "format-bash-command handles keywords in env"
  (sh/emit-form "lein run" :environment {:CIRCLE_ENV :production
                                           :SWANK true})
  => "export CIRCLE_ENV=production\nexport SWANK=true\nlein run\n")

(fact "format-bash-command handles env and pwd"
  (sh/emit-form "lein run"
                  :pwd "/home/test"
                  :environment {"CIRCLE_ENV" :production
                                "SWANK" true})
  => "cd /home/test\nexport CIRCLE_ENV=production\nexport SWANK=true\nlein run\n")

(fact "sh works"
  (let [resp (sh/sh (sh/quasiquote (hostname)))]
    resp => (just (clojure.java.shell/sh "hostname" :return-map true))))

(fact "sh w/ pwd works"
  (let [resp (sh/sh (sh/quasiquote (stat zero))
                    :pwd "/dev")]
    (-> resp :exit) => 0))