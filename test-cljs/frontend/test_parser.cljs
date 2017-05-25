(ns frontend.test-parser
  (:require [bodhi.core :as bodhi]
            [bodhi.param-indexing :as param-indexing]
            [cljs.test :refer-macros [is testing]]
            [frontend.parser :as parser]
            [om.next :as om-next])
  (:require-macros [devcards.core :as dc :refer [deftest]]))

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

(deftest connection-read-works
  (let [parser (om-next/parser
                {:read (bodhi/read-fn
                        (-> bodhi/basic-read
                            ;; Throw in param-indexing to prove that we remove
                            ;; the connection-related params before passing the
                            ;; read along.
                            param-indexing/read
                            parser/connection-read))})
        state (atom {:root/pets-connection {:connection/total-count 5
                                            :connection/edges [{:edge/node {:pet/name "Milo"
                                                                            :pet/species :pet-species/cat
                                                                            :pet/description "orange tabby"}}
                                                               {:edge/node {:pet/name "Otis"
                                                                            :pet/species :pet-species/dog
                                                                            :pet/description "pug"}}
                                                               {:edge/node {:pet/name "Chance"
                                                                            :pet/species :pet-species/dog
                                                                            :pet/description "American bulldog"}}
                                                               {:edge/node {:pet/name "Shadow"
                                                                            :pet/species :pet-species/dog
                                                                            :pet/description "golden retriever"}}
                                                               {:edge/node {:pet/name "Sassy"
                                                                            :pet/species :pet-species/cat
                                                                            :pet/description "Himalayan"}}]}})]

    (testing "Locally"
      (testing "without an offset or limit, returns all edges."
        (is (= {:root/pets-connection {:connection/total-count 5
                                       :connection/edges [{:edge/node {:pet/name "Milo"}}
                                                          {:edge/node {:pet/name "Otis"}}
                                                          {:edge/node {:pet/name "Chance"}}
                                                          {:edge/node {:pet/name "Shadow"}}
                                                          {:edge/node {:pet/name "Sassy"}}]}}
               (parser {:state state} '[{:root/pets-connection
                                         [:connection/total-count
                                          {:connection/edges [{:edge/node [:pet/name]}]}]}] nil))))

      (testing "with a offset and limit, returns the slice specified."
        (is (= {:root/pets-connection {:connection/total-count 5
                                       :connection/offset 1
                                       :connection/limit 2
                                       :connection/edges [{:edge/node {:pet/name "Otis"}}
                                                          {:edge/node {:pet/name "Chance"}}]}}
               (parser {:state state} '[{(:root/pets-connection {:connection/offset 1 :connection/limit 2})
                                         [:connection/total-count
                                          :connection/offset
                                          :connection/limit
                                          {:connection/edges [{:edge/node [:pet/name]}]}]}] nil))))

      (testing "with a limit which reaches past the end of the collection, returns as much as exists."
        (is (= {:root/pets-connection {:connection/total-count 5
                                       :connection/edges [{:edge/node {:pet/name "Sassy"}}]}}
               (parser {:state state} '[{(:root/pets-connection {:connection/offset 4 :connection/limit 2})
                                         [:connection/total-count
                                          {:connection/edges [{:edge/node [:pet/name]}]}]}] nil))))

      (testing "with an offset which starts past the end of the collection, returns no edges."
        (is (= {:root/pets-connection {:connection/total-count 5
                                       :connection/edges []}}
               (parser {:state state} '[{(:root/pets-connection {:connection/offset 6 :connection/limit 2})
                                         [:connection/total-count
                                          {:connection/edges [{:edge/node [:pet/name]}]}]}] nil))))

      (testing "with only an offset or a limit, throws."
        (is (thrown? js/Error (parser {:state state} '[{(:root/pets-connection {:connection/offset 4})
                                                        [:connection/total-count
                                                         {:connection/edges [{:edge/node [:pet/name]}]}]}] nil)))
        (is (thrown? js/Error (parser {:state state} '[{(:root/pets-connection {:connection/limit 2})
                                                        [:connection/total-count
                                                         {:connection/edges [{:edge/node [:pet/name]}]}]}] nil)))))

    (testing "Remotely"
      (testing "strips all offset and limit stuff, as it's not supported yet."
        (is (= '[{:root/pets-connection
                  [:connection/total-count
                   {:connection/edges [{:edge/node [:pet/name]}]}]}]
               (parser {:state state} '[{(:root/pets-connection {:connection/offset 1 :connection/limit 2})
                                         [:connection/total-count
                                          :connection/offset
                                          :connection/limit
                                          {:connection/edges [{:edge/node [:pet/name]}]}]}] :remote)))))))
