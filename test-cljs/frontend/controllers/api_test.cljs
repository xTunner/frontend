(ns frontend.controllers.api-test
  (:require [cemerick.cljs.test :as test]
            [frontend.controllers.ws :as ws]
            [frontend.state :as state]
            [frontend.controllers.api :as api]
            [frontend.utils :as utils :include-macros true])
  (:require-macros [cemerick.cljs.test :refer (is deftest testing)]))


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