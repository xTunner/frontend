(ns frontend.test-parser
  (:require [cljs.test :refer-macros [is deftest testing]]
            [frontend.parser :as parser]
            [om.next :as om-next]))

(deftest subpage-route-data-works
  (let [query [{:app/subpage-route-data {:a-subpage [{:some/key [:key/deeper-key]}]
                                         :another-subpage [{:some/other-key [:other-key/deeper-key]}]}}]
        env-when-at-subpage-route (fn [subpage-route]
                                    {:state (atom {:app/subpage-route subpage-route
                                                   :some/key {:key/deeper-key "some value"}
                                                   :some/other-key {:other-key/deeper-key "some other value"}})})]
    (is (= {:app/subpage-route-data {:some/key {:key/deeper-key "some value"}}}
           (parser/parser (env-when-at-subpage-route :a-subpage) query nil)))
    (is (= {:app/subpage-route-data {:some/other-key {:other-key/deeper-key "some other value"}}}
           (parser/parser (env-when-at-subpage-route :another-subpage) query nil)))))

(deftest route-data-reads-from-idents
  (let [query [{:app/route-data [{:route-data/organization [:organization/avatar-url]}
                                 ;; We don't have a good scalar key for a
                                 ;; workflow at the moment, but that's okay; any
                                 ;; key will do.
                                 {:route-data/workflow [::made-up-key]}]}]
        env {:state (atom {:app/route-data {:organization/vcs-type "github"
                                            :organization/name "acme"
                                            :project/name "anvil"
                                            :workflow/name "build-test-deploy"}
                           :organization/by-vcs-type-and-name {{:organization/vcs-type "github"
                                                                :organization/name "acme"}
                                                               {:organization/avatar-url "http://i.imgur.com/UaQcTEe.jpg"}}
                           :workflow/by-org-project-and-name {{:organization/vcs-type "github"
                                                               :organization/name "acme"
                                                               :project/name "anvil"
                                                               :workflow/name "build-test-deploy"}
                                                              {::made-up-key 5}}})}]
    (testing "local reads read from the matching table(s)"
      (is (= {:app/route-data {:route-data/organization {:organization/avatar-url "http://i.imgur.com/UaQcTEe.jpg"}
                               :route-data/workflow {::made-up-key 5}}}
             (parser/parser env query nil))))

    (testing "remote reads translate to ident query-roots"
      (let [remote-query (parser/parser env query :remote)]
        (is (= [[{:app/route-data [{[:organization/by-vcs-type-and-name {:organization/vcs-type "github"
                                                                         :organization/name "acme"}]
                                    [:organization/avatar-url]}

                                   {[:workflow/by-org-project-and-name {:organization/vcs-type "github"
                                                                        :organization/name "acme"
                                                                        :project/name "anvil"
                                                                        :workflow/name "build-test-deploy"}]
                                    [::made-up-key]}]}]]
               remote-query))
        (is (every? #(:query-root (meta %))
                    (:app/route-data (ffirst remote-query))))))))

(deftest route-data-skips-entities-missing-from-route-data
  (let [query [{:app/route-data [{:route-data/organization [:organization/avatar-url]}
                                 {:route-data/workflow [::made-up-key]}]}]
        env {:state (atom {:app/route-data {:organization/vcs-type "github"
                                            :organization/name "acme"}
                           :organization/by-vcs-type-and-name {{:organization/vcs-type "github"
                                                                :organization/name "acme"}
                                                               {:organization/avatar-url "http://i.imgur.com/UaQcTEe.jpg"}}
                           :workflow/by-org-project-and-name {{:organization/vcs-type "github"
                                                               :organization/name "acme"
                                                               :project/name "anvil"
                                                               :workflow/name "build-test-deploy"}
                                                              {::made-up-key 5}}})}]
    (testing "local reads skip keys which don't have enough information in the route data map"
      (is (= {:app/route-data {:route-data/organization {:organization/avatar-url "http://i.imgur.com/UaQcTEe.jpg"}}}
             (parser/parser env query nil))))

    (testing "local reads skip keys which don't have enough information in the route data map"
      (is (= [[{:app/route-data [{[:organization/by-vcs-type-and-name {:organization/vcs-type "github"
                                                                       :organization/name "acme"}]
                                  [:organization/avatar-url]}]}]]
             (parser/parser env query :remote))))))

(deftest route-data-skips-remote-entirely-when-all-children-are-skipped
  (let [query [{:app/route-data [{:route-data/organization [:organization/avatar-url]}
                                 {:route-data/workflow [::made-up-key]}]}]
        env {:state (atom {:app/route-data {}
                           :organization/by-vcs-type-and-name {{:organization/vcs-type "github"
                                                                :organization/name "acme"}
                                                               {:organization/avatar-url "http://i.imgur.com/UaQcTEe.jpg"}}
                           :workflow/by-org-project-and-name {{:organization/vcs-type "github"
                                                               :organization/name "acme"
                                                               :project/name "anvil"
                                                               :workflow/name "build-test-deploy"}
                                                              {::made-up-key 5}}})}]
    (is (= {:app/route-data {}}
           (parser/parser env query nil)))

    (is (= [] (parser/parser env query :remote)))))
