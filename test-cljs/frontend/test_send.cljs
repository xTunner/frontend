(ns frontend.test-send
  (:require [clojure.test :refer-macros [deftest testing is]]
            [frontend.send :as send]))

(deftest org-runs-ast?-works
  (let [example {:key :circleci/organization
                 :type :join
                 :children [{:key :organization/name
                             :type :prop}
                            {:key :organization/vcs-type
                             :type :prop}
                            {:key :organization/workflow-runs
                             :type :join
                             :children [{:type :prop
                                         :key :workflow-run/id}]}]}]
   (testing "returns true when ast asks for org workflow runs"
     (is (= true
            (#'send/org-runs-ast? example))))
   (testing "returns false when ast doesn't ask for an org's workflow-runs"
     (is (= false
            (#'send/org-runs-ast? (update example :children pop)))))
   (testing "returns false when ast asks for additional org data"
     (is (= false
            (#'send/org-runs-ast? (update example
                                          :children
                                          conj
                                          {:key :organization/projects
                                           :type :join
                                           :children [{:type :prop
                                                       :key :project/name}]})))))
   (testing "returns false when ast has the wrong key"
     (is (= false
            (#'send/org-runs-ast? (assoc example :key :some-other-key)))))))

(deftest branch-crumbs-ast?-works
  (let [example-ast {:type :join
                     :key :circleci/organization
                     :children
                     [{:type :join
                       :key :organization/project
                       :children
                       [{:type :join
                         :key :project/branch
                         :children
                         [{:type :prop
                           :key :branch/name}]}]}]}]
    (testing "returns true when ast asks for routed branch's name"
      (is (= true (#'send/branch-crumb-ast? example-ast))))
    (testing "returns false when ast has the wrong key"
      (is (= false
             (#'send/branch-crumb-ast? (assoc example-ast :key :some-key)))))
    (testing "returns false when ast asks for additional branch data"
      (is (= false
             (#'send/branch-crumb-ast?
              (-> example-ast
                  (update-in [:children 0 :children 0 :children]
                             conj
                             {:type :join
                              :key :branch/workflow-runs
                              :children []}))))))
    (testing "returns false when ast asks for additional project data"
      (is (= false
             (#'send/branch-crumb-ast? (update-in example-ast
                                                  [:children 0 :children]
                                                  conj
                                                  {:type :prop
                                                   :key :project/name})))))
    (testing "returns false when ast asks for additional org data"
      (is (= false
             (#'send/branch-crumb-ast? (update example-ast
                                               :children
                                               conj
                                               {:type :prop
                                                :key :organization/vcs-type})))))
    (testing "returns false when ast doesn't ask for branch name"
      (is (= false
             (#'send/branch-runs-ast?
              (assoc-in example-ast
                        [:children 0 :children 0 :children 0]
                        {:type :join
                         :key :branch/workflow-runs
                         :children []}))))
      (is (= false
             (#'send/branch-runs-ast?
              (update-in example-ast
                         [:children 0 :children]
                         pop)))))))

(deftest branch-runs-ast?-works
  (let [example-ast {:type :join
                     :key :circleci/organization
                     :children
                     [{:type :join
                       :key :organization/project
                       :children
                       [{:type :join
                         :key :project/branch
                         :children
                         [{:type :join
                           :key :branch/workflow-runs
                           :children []}
                          {:type :join
                           :key :branch/project
                           :children
                           [{:type :prop
                             :key :project/name}
                            {:type :join
                             :key :project/organization
                             :children
                             [{:type :prop
                               :key :organization/name}]}]}]}]}]}]
    (testing "returns true when ast asks for branch runs"
      (is (= true (#'send/branch-runs-ast? example-ast))))
    (testing "returns false when ast has the wrong key"
      (is (= false
             (#'send/branch-runs-ast?
              (assoc example-ast :key :some-other-key)))))
    (testing "returns false when ast asks for additional branch data"
      (is (= false
             (#'send/branch-runs-ast?
              (update-in example-ast
                         [:children 0 :children 0 :children]
                         conj
                         {:type :prop
                          :key :branch/name})))))
    (testing "returns false when ast asks for additional project data"
      (is (= false
             (#'send/branch-crumb-ast? (update-in example-ast
                                                  [:children 0 :children]
                                                  conj
                                                  {:type :prop
                                                   :key :project/name})))))
    (testing "returns false when ast asks for additional org data"
      (is (= false
             (#'send/branch-crumb-ast? (update example-ast
                                               :children
                                               conj
                                               {:type :prop
                                                :key :organization/vcs-type})))))
    (testing "returns false when ast doesn't ask for branch runs"
      (is (= false
             (#'send/branch-runs-ast?
              (assoc-in example-ast
                        [:children 0 :children 0 :children 0]
                        {:type :prop
                         :key :branch/name}))))
      (is (= false
             (#'send/branch-runs-ast?
              (update-in example-ast
                         [:children 0 :children]
                         pop)))))))

(deftest project-runs-ast?-works
  (let [example-ast {:type :join
                     :key :circleci/organization
                     :children
                     [{:type :join
                       :key :organization/project
                       :children
                       [{:type :prop
                         :key :project/name}
                        {:type :join
                         :key :project/organization
                         :children
                         [{:type :prop
                           :key :organization/name}
                          {:type :prop
                           :key :organization/vcs-type}]}
                        {:type :join
                         :key :project/workflow-runs
                         :children []}]}]}]
    (testing "returns true when ast asks for project runs"
      (is (= true
             (#'send/project-runs-ast? example-ast))))
    (testing "returns false when ast has wrong key"
      (is (= false
             (#'send/project-runs-ast?
              (assoc example-ast :key :some-other-key)))))
    (testing "returns false when ast asks for additional org data"
      (is (= false
             (#'send/project-runs-ast?
              (update example-ast
                      :children
                      conj
                      {:type :prop
                       :key :organization/vcs-type}))))
      (is (= false
             (#'send/project-runs-ast?
              (update-in example-ast
                         [:children 0 :children 1 :children]
                         conj
                         {:type :join
                          :key :organization/projects
                          :children []})))))
    (testing "returns false when ast doesn't ask for runs"
      (is (= false
             (#'send/project-runs-ast?
              (update-in example-ast
                         [:children 0 :children]
                         pop)))))
    (testing "returns false when ast doesn't ask for project info"
      (is (= false
             (#'send/project-runs-ast?
              (update-in example-ast
                         [:children 0 :children]
                         (fn [children]
                           (remove #(= :project/organization
                                       (:key %))
                                   children))))))
      (is (= false
             (#'send/project-runs-ast?
              (update-in example-ast
                         [:children 0 :children]
                         (fn [children]
                           (remove #(= :project/name
                                       (:key %))
                                   children)))))))))

(deftest project-crumb-ast?-works
  (let [example {:type :join
                 :key :circleci/organization
                 :children
                 [{:type :join
                   :key :organization/project
                   :children
                   [{:type :prop
                     :key :project/name}]}]}]
    (testing "returns true when ast asks for project name"
      (is (= true
             (#'send/project-crumb-ast? example))))
    (testing "returns false when ast asks for additional org info"
      (is (= false
             (#'send/project-crumb-ast?
              (update example
                      :children
                      conj
                      {:type :prop
                       :key :organization/vcs-type})))))
    (testing "returns false when ast asks for additional project info"
      (is (= false
             (#'send/project-crumb-ast? (update-in example
                                                   [:children 0 :children]
                                                   conj
                                                   {:type :join
                                                    :key :project/workflow-runs
                                                    :children []})))))
    (testing "returns false when ast has wrong key"
      (is (= false
             (#'send/project-crumb-ast?
              (assoc example :key :some-other-key)))))
    (testing "returns false when ast does not ask for project name"
      (is (= false
             (#'send/project-crumb-ast? (update-in example
                                                   [:children 0 :children]
                                                   pop)))))))

(deftest run-page-crumbs-ast?-works
  (let [example {:type :join
                 :key :circleci/run
                 :children
                 [{:type :prop
                   :key :run/id}
                  {:type :join
                   :key :run/project
                   :children
                   [{:type :prop
                     :key :project/name}
                    {:type :join
                     :key :project/organization
                     :children
                     [{:type :prop
                       :key :organization/vcs-type}
                      {:type :prop
                       :key :organization/name}]}]}
                  {:type :join
                   :key :run/trigger-info
                   :children
                   [{:type :prop
                     :key :trigger-info/branch}]}]}]
    (testing "returns true when ast is for run-page crumbs"
      (is (= true
             (#'send/run-page-crumbs-ast? example))))
    (testing "returns false when ast has wrong key"
      (is (= false
             (#'send/run-page-crumbs-ast?
              (assoc example :key :circleci/organization)))))
    (testing "returns false when ast asks for extra run info"
      (is (= false
             (#'send/run-page-crumbs-ast?
              (update example
                      :children
                      conj
                      {:type :prop
                       :key :run/started-at})))))
    (testing "returns false when ast asks for less run info"
      (is (= false
             (#'send/run-page-crumbs-ast?
              (update example :children pop)))))))
