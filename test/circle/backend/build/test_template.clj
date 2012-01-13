(ns circle.backend.build.test-template
  (:use midje.sweet)
  (:use [circle.backend.build.template :exclude (find)]))

(fact "apply-template works"
  (count (apply-template :foo-template [{:name "foo" :act-fn (fn [] (println "foo!"))}])) => 3
  (provided
    (build-templates) => {:foo-template {:prefix [(fn []
                                                    {:name "prefix action"})]
                                         :suffix [(fn []
                                                    {:name "suffix action"})]}}))
