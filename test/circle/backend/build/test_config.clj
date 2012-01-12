(ns circle.backend.build.test-config
  (:require [clojure.string :as str])
  (:use midje.sweet)
  (:use circle.backend.build.config)
  (:use [circle.backend.build :only (checkout-dir valid? validate successful?)])
  (:use [circle.backend.action.bash :only (bash remote-bash-build)])
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.build.test-utils :as test])
  (:require [circle.model.project :as project])
  (:require [circle.model.spec :as spec])
  (:require [circle.backend.build.inference :as inference])
  (:require circle.init)
  (:use [arohner.utils :only (inspect)])
  (:use [circle.util.predicates :only (ref?)]))

(circle.init/init)
(test/ensure-circle-project)
(test/ensure-test-project)
(fact "parse-action-map works"
  (against-background
    ;;; Stub checkout dir to be /usr. Later, we will pass "bin" to :pwd, so the ls will run in /usr/bin
    (checkout-dir anything) => "/usr")
  (let [dir "bin"
        cmd {(keyword "ls") {:environment {:CIRCLE_ENV "production", :SWANK "true"}
                             :pwd dir}}
        b (test/minimal-build :actions [(parse-action-map cmd)])
        expected-pwd (format "%s/%s" (checkout-dir b) dir)
        _ (run/run-build b)]
    (successful? b) => truthy

    ;; ssh into localhost, ls /usr/bin. Assert the output of ls
    ;; contains some well-known files, proving that the :pwd was set
    ;; properly
    (-> @b :action-results (first) :out (first) :message (str/split #"\n") (set) (contains? "cd"))))

(fact "load-job works"
  (load-job test/circle-config :build) => truthy)

(fact "template/find works keyword name"
  (circle.backend.build.template/find :build) => truthy)

(fact "template/find works with strings"
  (circle.backend.build.template/find "build") => truthy)

(fact "build-from-config works"
  (let [project test/circle-project
        config (get-config-for-url (-> test/circle-project :vcs_url))
        vcs_revision "9538736fc7e853db8dac3a6d2f35d6dcad8ec917"
        b (build-from-config config test/circle-project
                             :vcs-revision vcs_revision
                             :job-name :build)]
    (ref? b) => true
    (-> @b :vcs_revision) => "9538736fc7e853db8dac3a6d2f35d6dcad8ec917"
    (validate @b) => nil))

(fact "build-from-json works"
  (let [build (build-from-json test/circle-github-json)]
    (ref? build) => true
    (-> @build :vcs_revision) => "9538736fc7e853db8dac3a6d2f35d6dcad8ec917"))

(fact "build loads the node and slurps the ssh keys"
  ;; The circle.yml contains :private-key, :public-key. Verify they were slurped.
  (let [build (build-from-json test/circle-github-json)]
    (-> @build :node) => map?
    (-> @build :node :username) => "ubuntu"
    (-> @build :node :public-key) => #"^ssh-rsa"
    (-> @build :node :private-key) => #"BEGIN RSA PRIVATE KEY"
    (validate @build) => nil))

(tabular
 (fact "build-from-url works"
   (do
     (test/ensure-project {:vcs_url ?url})
     (infer-build-from-url ?url)) => ref?)
 ?url
 "https://github.com/arohner/CircleCI"
 "https://github.com/arohner/circle-dummy-project")

(tabular
 (fact "infer project name works"
   (infer-project-name ?url) => ?expected)
 ?url ?expected
 "https://github.com/rails/rails.git" "rails")
