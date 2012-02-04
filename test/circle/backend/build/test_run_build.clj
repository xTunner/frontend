(ns circle.backend.build.test-run-build
  (:use midje.sweet)
  (:use [circle.backend.build.test-utils])
  (:use [circle.backend.action :only (defaction action)])
  (:require [circle.backend.action.nodes :as nodes])
  (:use [circle.model.build :only (build successful?)])
  (:use [circle.backend.build.run :only (run-build)])
  (:require [circle.model.project :as project])
  (:use [circle.backend.build.config :only (build-from-url)])
  (:require [circle.backend.nodes.rails :as rails])
  (:require circle.system)
  (:require [somnium.congomongo :as mongo])
  (:use [arohner.utils :only (inspect)])
  (:require [circle.backend.ec2 :as ec2])
  (:use [circle.util.predicates :only (ref?)])
  (:use [circle.util.retry :only (wait-for)]))

(defaction successful-action [act-name]
  {:name act-name}
  (fn [build]
    nil))

(defn successful-build []
  (minimal-build :actions [(successful-action "1")
                           (successful-action "2")
                           (successful-action "3")]))

(fact "successful build is successful"
  (let [build (run-build (successful-build))]
    build => ref?
    @build => map?
    (-> @build :action-results) => seq
    (-> @build :action-results (count)) => 3
    (for [res (-> @build :action-results)]
      (> (-> res :stop-time) (-> res :start-time)) => true)
    (successful? build) => truthy))

(fact "dummy project does not start nodes"
  ;;; This should be using the empty template, which does not start nodes
  (-> "https://github.com/arohner/circle-dummy-project"
      (build-from-url)
      deref
      :actions
      (count)) => 0)

(fact "running an empty test does not generate an infrastructure_fail"
  (let [build (run-build (minimal-build))]
    (-> @build :infrastructure_fail) => falsey))

(fact "build of dummy project is successful"
  (-> "https://github.com/arohner/circle-dummy-project" (build-from-url) (run-build) (successful?)) => true)

(fact "builds insert into the DB"
  (let [build (run-build (successful-build))]
    (successful? build) => truthy
    (-> @build :_project_id) => truthy
    (-> @build :build_num) => integer?
    (-> @build :build_num) => pos?
    (let [builds (mongo/fetch :builds :where {:_id (-> @build :_id)})]
      (count builds) => 1)))

(fact "builds using the provided objectid"
  (let [id (org.bson.types.ObjectId.)
        _ (mongo/insert! :builds {:_id id})
        build (run-build (successful-build) :id (str id))
        build-db (mongo/fetch-one :builds :where {:_id id})]
    build-db => truthy
    (-> build-db :stop_time) => truthy))

(fact "successive builds use incrementing build-nums"
  (let [first-build (run-build (successful-build))
        second-build (run-build (successful-build))]
    (> (-> @second-build :build_num) (-> @first-build :build_num)) => true))

(fact "running an inferred build with zero actions marks the project disabled"
  (let [build (minimal-build :actions [])]
    (dosync
     (run-build build) => anything
     (-> (mongo/fetch-one :projects :where {:vcs_url (-> (test-project) :vcs_url)}) :state) => "disabled")))

(fact "running a disabled build"
  (let [build (minimal-build :actions [])
        _ (project/set-uninferrable (test-project))]
    (run-build build) => anything
    (-> @build :error_message) => string?
    (-> @build :stop_time) => truthy
    (provided
      (circle.backend.build.run/do-build* anything) => anything :times 0)))

;; This is the only test that should start an instnace
(fact "the customer AMI starts up"
  (let [build (run-build (minimal-build :actions [(nodes/start-nodes)]
                                        :node rails/rails-node))]
    (-> @build :instance-id (ec2/instance) :state :name) => "running"
    (-> @build :instance-ids (first)) => string?
    (nodes/cleanup-nodes build) => anything
    (wait-for {:sleep 1000 :tries 5}
              #(not= (-> @build :instance-ids (first) (ec2/instance) :state :name) "running")) => anything
    (-> @build :instance-ids (first) (ec2/instance) :state :name) =not=> "running"))
