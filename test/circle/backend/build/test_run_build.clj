(ns circle.backend.build.test-run-build
  (:use midje.sweet)
  (:use [circle.backend.action :only (defaction)])
  (:use [circle.backend.build :only (build successful?)])
  (:use [circle.backend.build.run :only (run-build)]))

(defaction successful-action [act-name]
  {:name act-name}
  (fn [context]
    {:successful true :continue true}))

(defaction fail-action [fail]
  {:name "I'm a fail action"}
  (fn [context]
    {:successful false :continue true}))

(def successful-build
  (build :project-name "succesful build"
         :actions [(successful-action "1")
                   (successful-action "2")
                   (successful-action "3")]))

(fact "successful build is successful"
  (let [build (run-build successful-build)]
    build => map?
    (-> build :action-results) => seq
    (-> build :action-results (count)) => 3
    (successful? build) => truthy))