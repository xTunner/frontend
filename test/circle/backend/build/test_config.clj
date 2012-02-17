(ns circle.backend.build.test-config
  (:require [clojure.string :as str])
  (:use midje.sweet)
  (:use circle.backend.build.config)
  (:use [circle.model.build :only (checkout-dir valid? validate successful?)])
  (:use [circle.backend.action.bash :only (bash remote-bash-build)])
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.build.test-utils :as test])
  (:require [circle.model.project :as project])
  (:require [circle.model.spec :as spec])
  (:require [circle.backend.build.inference :as inference])
  (:require circle.init)
  (:use [circle.util.predicates :only (ref?)]))

(test/test-ns-setup)

(fact "parse-action-map works"
  (against-background
    ;;; Stub checkout dir to be /usr. Later, we will pass "bin" to :pwd, so the ls will run in /usr/bin
    (checkout-dir anything) => "/")
  (let [dir "usr"
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

;; Test inferred-config
(fact "get-db-config returns falsy for empty project"
  (let [project test/test-project
        repo (test/test-repo "empty-project")
        inferred-config (infer-config repo)
        db-config (get-db-config project inferred-config)]
    db-config => nil))


;; Test get-db-config
(fact "get-db-config returns falsy for empty project"
  (let [project test/test-project
        repo (test/test-repo "empty-project")
        inferred-config (infer-config repo)
        db-config (get-db-config project inferred-config)]
    db-config => nil))

(fact "get-db-config has inferred and speced steps project"
  (let [project test/test-project
        project (merge project {:test "echo a\necho b"})
        repo (test/test-repo "database_yml_1")
        inferred-config (infer-config repo)
        db-config (get-db-config project inferred-config)]
    db-config =not=> nil
    (-> db-config :actions) => (contains [(contains {:source :inferred :name "Generate database.yml" :type :setup})
                                          (contains {:source :spec :command "echo a" :type :test})]
                                         :gaps-ok)))

(fact "build-from-url works"
  (let [project test/test-project
        vcs_revision "78f58846a049bb6772dcb298163b52c4657c7d45"
        b (build-from-url (-> project :vcs_url)
                          :vcs-revision vcs_revision
                          :job-name :build)]
    (run/configure b) => anything
    b => ref?
    @b => (contains {:vcs_url string?
                     :vcs_revision "78f58846a049bb6772dcb298163b52c4657c7d45"
                     :vcs-private-key string?
                     :build_num pos?})

    (-> @b :actions (count)) => 1 ; one step, the config/inference step.

    (validate @b) => nil))


(fact "build-from-url :job-name :deploy works"
  (let [project test/yml-project
        b (build-from-url (-> project :vcs_url)
                          :job-name :deploy)
        _ (run/configure b)]
    @b => (contains {:vcs_url "https://github.com/circleci/test-yml"
                     :job-name :deploy})
    (-> @b :actions) => (contains [(contains {:name "start nodes"
                                              :type :infrastructure
                                              :source :template})
                                   (contains {:name #"nginx"
                                              :source :spec})]
                                  :gaps-ok)))

(fact "build-from-url :infer flag works"
  (let [project test/yml-project
        b (build-from-url (-> project :vcs_url)
                          :infer true)]
    (-> @b :actions) =not=> (contains {:name #"nginx"})
    (-> @b :actions) =not=> (contains {:source :spec})

    (-> @b :actions) =contains=> {:name #"bundle install"
                                  :source :inferred}))

(fact "build-from-url builds from database"
  (let [project test/partially-inferred-project
        b (build-from-url (-> project :vcs_url))]
    (-> @b :actions) =contains=> {:name "echo a" :type :test :source :spec}
    (-> @b :actions) =not=> (contains {:type :setup :source :inferred})))

(fact "build-from-json works"
  (let [build (build-from-json test/circle-dummy-project-json)]
    (ref? build) => true
    (-> @build :vcs_revision) => "78f58846a049bb6772dcb298163b52c4657c7d45"))

(fact "build-from-url works for yaml configs"
  (build-from-url "https://github.com/circleci/test-yml") => ref?)

(fact "parse-spec-actions support different kinds of newline"
  (-> :setup ((parse-spec-actions {:setup "1\n2"}))) => (maps-containing {:name "1"} {:name "2"})
  (-> :setup ((parse-spec-actions {:setup "1\r\n2"}))) => (maps-containing {:name "1"} {:name "2"}))


(fact "circle is not inferred"
  ;; not a real "test", but currently circle shouldn't be inferred,
  ;; and causes hard to find test failures when it's not.
  (let [json test/circle-github-json
        p (project/get-by-url (-> json :repository :url))]
    (-> p :inferred) => falsey
    (-> (build-from-json json) (run/configure) (deref) :job-name) => :build))

(fact "circle deploys have :lb-name"
  (-> (circle.backend.build.config/build-from-url "https://github.com/circleci/test-yml" :job-name :deploy)
      (run/configure)
      (deref)
      :lb-name) => truthy)

(fact "builds w/ yaml have a :node"
  (let [build (-> (circle.backend.build.config/build-from-url "https://github.com/arohner/circle-dummy-project")
                  (run/configure))]
    (-> @build :node :ami) => truthy))

(fact "inferred builds have a :node"
  (let [build (-> (circle.backend.build.config/build-from-url "https://github.com/arohner/circle-empty-repo")
                  (run/configure))]
    (-> @build :node :ami) => truthy))
