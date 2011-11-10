(ns circle.backend.build.test-run-build
  (:use midje.sweet)
  (:use [circle.backend.build.utils :only (minimal-build)])
  (:use [circle.backend.action :only (defaction)])
  (:use [circle.backend.build :only (build successful?)])
  (:use [circle.backend.build.run :only (run-build)])
  (:use [circle.util.predicates :only (ref?)]))

(circle.db/init)

(defaction successful-action [act-name]
  {:name act-name}
  (fn [build]
    nil))

(defn successful-build []
  (build {:project-name "succesful build"
          :build-num 1
          :vcs-url "git@github.com:foo/bar.git"
          :vcs-revision "f00b4r"
          :actions [(successful-action "1")
                    (successful-action "2")
                    (successful-action "3")]}))

(fact "successful build is successful"
  (let [build (run-build (successful-build))]
    build => ref?
    @build => map?
    (-> @build :action-results) => seq
    (-> @build :action-results (count)) => 3
    (for [res (-> @build :action-results)]
      (> (-> res :stop-time) (-> res :start-time)) => true)
    (successful? build) => truthy))


(defchecker email-args [& args]
  (println "checking email args:" args)
  true)

(fact "notify email works"
  (successful? (run-build (minimal-build :actions [(successful-action "1")]
                                         :notify-email ["foo@bar.com"
                                                        "baz@bar.com"]))) => truthy
  (provided
    (circle.backend.email/send :to "foo@bar.com" :subject anything :body anything) => anything :times 1
    (circle.backend.email/send :to "baz@bar.com" :subject anything :body anything) => anything :times 1))

(fact "notify email handles :owner"
  (binding [circle.backend.email/send-email? false]
    (successful? (run-build (minimal-build :actions [(successful-action "1")]
                                           :repository {:owner {:name "arohner"
                                                                :email "a@circleci.com"}}
                                           :notify-email [:owner]))) => truthy) 
  (provided
    (circle.backend.email/send :to "a@circleci.com" :subject anything :body anything) => anything :times 1))

(fact "notify email handles :committer"
  (binding [circle.backend.email/send-email? false]
    (successful? (run-build (minimal-build :actions [(successful-action "1")]
                                           :commits [{:author {:name "Allen"
                                                               :email "a@circleci.com"}}]
                                           :notify-email [:committer]))) => truthy) 
  (provided
    (circle.backend.email/send :to "a@circleci.com" :subject anything :body anything) => anything :times 1))

(fact "notify email handles multiple committers"
  (binding [circle.backend.email/send-email? false]
    (successful? (run-build (minimal-build :actions [(successful-action "1")]
                                           :commits [{:author {:name "Allen"
                                                               :email "allen@circleci.com"}}
                                                     {:author {:name "Paul"
                                                               :email "paul@circleci.com"}}]
                                           :notify-email [:committer]))) => truthy) 
  (provided
    (circle.backend.email/send :to "allen@circleci.com" :subject anything :body anything) => anything :times 1
    (circle.backend.email/send :to "paul@circleci.com" :subject anything :body anything) => anything :times 1))

(fact "build emails contain the name of the project"
  (binding [circle.backend.email/send-email? false]
    (successful? (run-build (minimal-build :project-name "Test Project"
                                           :actions [(successful-action "1")]
                                           :commits [{:author {:name "Allen"
                                                               :email "a@circleci.com"}}]
                                           :notify-email [:committer]))) => truthy)
  (provided
    (circle.backend.email/send :to anything :subject anything :body #"Test Project") => anything :times 1))