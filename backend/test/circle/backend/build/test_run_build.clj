(ns circle.backend.build.test-run-build
  (:use midje.sweet)
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