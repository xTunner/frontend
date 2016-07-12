(ns frontend.controllers.test-api
  (:require [frontend.controllers.ws :as ws]
            [frontend.state :as state]
            [frontend.controllers.api :as api]
            [frontend.utils :as utils :include-macros true]
            [cljs.test :refer-macros [deftest is testing]]))

(deftest follow-repo-event
  (testing "selected repo is marked as being followed"
    (let [repo1      {:following false :name "test1" :username "blah"}
          repo2      {:following false :name "test2" :username "blah"}
          context    {:name "test2"
                      :login "blah"}
          state      (assoc-in {} state/repos-path [repo1 repo2])

          new-state  (api/api-event nil :follow-repo :success {:context context} state)

          repo2-following? (get-in new-state (conj (state/repo-path 1) :following))
          repo1-following? (get-in new-state (conj (state/repo-path 0) :following))]
    (is repo2-following?)
    (is (not repo1-following?)))))

(deftest filter-piggieback
  (testing "piggiebacked orgs are removed from list"
    (let [orgs [{:login "foo1"
                 :avatar_url "http://localhost/foo1.png"
                 :org true
                 :piggieback_orgs ["piggie1" "piggie2"]}
                {:login "piggie1"
                 :avatar_url "http://localhost/piggie1.png"
                 :org true
                 :piggieback_orgs []}
                {:login "piggie2"
                 :avatar_url "http://localhost/piggie2.png"
                 :org true
                 :piggieback_orgs []}]
          filtered-orgs (api/filter-piggieback orgs)]
      (is (= filtered-orgs (take 1 orgs))))))
