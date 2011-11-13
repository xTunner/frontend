(ns circle.backend.build.test-action
  (:use midje.sweet)
  (:require circle.db)
  (:require [circle.backend.action :as action])
  (:require [circle.backend.build :as build])
  (:require [circle.backend.build.run :as run])
  (:use [circle.backend.build.utils :only (minimal-build)]))

(circle.db/init)

(action/defaction test-action [act-result]
  {:name "test"}
  (fn [build]
    (action/add-action-result act-result)))

(fact "add-action adds output strings"
  (let [build (minimal-build :actions [(test-action {:out "an out str"})])
        _ (run/run-build build)]
    (-> @build :action-results (count)) => 1
    (-> @build :action-results (first) :out (first) :message) => "an out str"))

(fact "add-action adds stderr strings"
  (let [build (minimal-build :actions [(test-action {:out "an out str"})
                                       (test-action {:err "an err str"})])
        _ (run/run-build build)]
    (-> @build :action-results (count)) => 2
    (-> @build :action-results (second) :out (first) :message) => "an err str"))

(fact "add-action appends output strings"
  (let [build (minimal-build :actions [(action/action :name "test-action"
                                                      :act-fn (fn [build]
                                                           (action/add-action-result {:out "first"})
                                                           (action/add-action-result {:out "second"})))])
        _ (run/run-build build)]
    (-> @build :action-results (count)) => 1
    (-> @build :action-results (first) :out) => vector?
    (-> @build :action-results (first) :out (count)) => 2
    (-> @build :action-results (first) :out (first) :message) => "first"
    (-> @build :action-results (first) :out (second) :message) => "second"))

(fact "abort! works"
  (let [build (minimal-build :actions [(test-action {:out "an out str"})
                                       (action/action :name "abort action"
                                                      :act-fn (fn [build]
                                                                (action/add-action-result {:out "first"})
                                                                (action/abort! build "test abort message")))])
        _ (run/run-build build)]
    (-> @build :action-results (count)) => 2
    (-> @build :action-results (second) :out (second) :type) => :err
    (-> @build :action-results (second) :out (second) :message) => "test abort message"
    (build/successful? build) => false))

