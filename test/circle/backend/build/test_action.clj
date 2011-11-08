(ns circle.backend.test_action
  (:use midje.sweet)
  (:require [circle.backend.action :as action])
  (:require [circle.backend.build :as build])
  (:require [circle.backend.build.run :as run]))

(defn minimal-build [& {:keys [actions]}]
  (build/build  {:project-name "test proj"
                 :build-num 1
                 :vcs-url "http://github.com/arohner/test-circle"
                 :actions (or actions [])}))

(action/defaction test-action [act-result]
  {:name "test"}
  (fn [build]
    (action/add-action-result act-result)))

(fact "add-action adds output strings"
  (let [build (minimal-build :actions [(test-action {:out "an out str"})])
        _ (run/run-build build)]
    (-> @build :action-results (count)) => 1
    (-> @build :action-results (first) :out (first)) => "an out str"))

(fact "add-action appends output strings"
  (let [build (minimal-build :actions [(action/action :name "test-action"
                                                      :act-fn (fn [build]
                                                           (action/add-action-result {:out "first"})
                                                           (action/add-action-result {:out "second"})))])
        _ (run/run-build build)]
    (-> @build :action-results (count)) => 1
    (-> @build :action-results (first) :out) => vector?
    (-> @build :action-results (first) :out (count)) => 2
    (-> @build :action-results (first) :out (first)) => "first"
    (-> @build :action-results (first) :out (second)) => "second"))

