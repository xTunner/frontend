(ns frontend.test-send
  (:require [clojure.test :refer-macros [deftest testing is]]
            [frontend.send :as send]))

(deftest find-child-by-key-works
  (testing "returns `nil` when ast has no children"
    (is (nil? (#'send/find-child-by-key {:type :prop :key :foo} :some-key))))
  (testing "returns first matching expr-ast when ast's children contains child with key"
      (let [expr-ast {:key :child-foo :type :prop}]
        (is (= expr-ast
               (#'send/find-child-by-key {:key :parent
                                          :type :join
                                          :children [{:type :prop :key :child-bar}
                                                     expr-ast
                                                     (assoc expr-ast
                                                            :type :join
                                                            :children [])]}
                                         (:key expr-ast))))))
  (testing "returns `nil` when ast does not contain child with key"
    (is (nil? (#'send/find-child-by-key {:key :parent
                                         :type :join
                                         :children [{:type :prop :key :a-child}
                                                    {:type :prop :key :another-child}]}
                                        :some-child)))))

(deftest org-runs-ast?-works
  (testing "returns false when ast doesn't ask for an org's workflow-runs"
    (is (= false
           (#'send/org-runs-ast? {:key :circleci/organization
                                  :type :join
                                  :children [{:key :organization/projects
                                              :type :join
                                              :children [{:type :prop
                                                          :key :project/name}]}]}))))
  (testing "returns true when ast looks like it asks for an org's workflow-runs"
    (is (= true
           (#'send/org-runs-ast? {:key :circleci/organization
                                  :type :join
                                  :children [{:key :organization/name
                                              :type :prop}
                                             {:key :organization/projects
                                              :type :join
                                              :children [{:type :prop
                                                          :key :project/name}
                                                         {:type :join
                                                          :key :project/workflow-runs
                                                          :children [{:type :prop
                                                                      :key :run/id}]}]}]}))))
  (testing "order of children doesn't matter"
    (is (= true
           (#'send/org-runs-ast? {:key :circleci/organization
                                  :type :join
                                  :children [{:key :organization/projects
                                              :type :join
                                              :children [{:type :join
                                                          :key :project/workflow-runs
                                                          :children [{:type :prop
                                                                      :key :run/id}]}
                                                         {:type :prop
                                                          :key :project/name}]}
                                             {:key :organization/name
                                              :type :prop}]})))))
