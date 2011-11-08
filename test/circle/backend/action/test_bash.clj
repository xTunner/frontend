(ns circle.backend.action.test-bash
  (:use midje.sweet)
  (:use [circle.backend.build :only (*pwd* *env*)])
  (:require [circle.backend.action.bash :as bash]))

(fact "format-bash-command handles pwd"
  (binding [*pwd* "/home/test"]
    (bash/format-bash-cmd "hostname") => "cd /home/test; hostname"))

(fact "format-bash-command handles env"
  (binding [*env* {"CIRCLE_ENV" :production
                   "SWANK" true}]
    (bash/format-bash-cmd "lein run") => "CIRCLE_ENV=production SWANK=true lein run"))

(fact "format-bash-command handles env and pwd"
  (binding [*pwd* "/home/test"
            *env* {"CIRCLE_ENV" :production
                   "SWANK" true}]
    (bash/format-bash-cmd "lein run") => "cd /home/test; CIRCLE_ENV=production SWANK=true lein run"))