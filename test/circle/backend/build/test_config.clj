(ns circle.backend.build.test-config
  (:use midje.sweet)
  (:use circle.backend.build.config)
  (:use [circle.backend.build :only (checkout-dir valid? validate)])
  (:use [circle.backend.action.bash :only (bash remote-bash-build)])
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.build.test-utils :as test])
  (:require circle.init)
  (:use [arohner.utils :only (inspect)])
  (:use [circle.util.predicates :only (ref?)]))

(circle.init/init)
(test/ensure-circle-project)
(test/ensure-test-project)


;; This test used to assume we could determine the pwd when creating
;; the build. That functionality has been moved, now we determine the
;; pwd when running the build.

;; (fact "parse-action-map works"
;;   (let [b (test/minimal-build)
;;         _ (println "build")
;;         dir "backend"
;;         expected-pwd (format "%s/%s" (checkout-dir b) dir)
;;         cmd {(keyword "lein daemon start \":web\"") {:environment {:CIRCLE_ENV "production", :SWANK "true"}}}]
;;     (parse-action-map cmd) => truthy
;;     (provided
;;       (remote-bash-build  b "lein daemon start \":web\"" :environment {:CIRCLE_ENV "production", :SWANK "true"} :pwd expected-pwd) => truthy :times 1)))

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
                             :vcs_revision vcs_revision
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
 "https://github.com/travis-ci/travis-ci"
 "https://github.com/arohner/CircleCI"
 "https://github.com/edavis10/redmine"
 "https://github.com/arohner/circle-dummy-project")

(fact "build email addresses are correct"
  (let [build (build-from-json test/circle-github-json)]
    (-> @build :notify_emails) => #{"arohner@gmail.com"}))

(tabular
 (fact "infer project name works"
   (infer-project-name ?url) => ?expected)
 ?url ?expected
 "https://github.com/rails/rails.git" "rails")