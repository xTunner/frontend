(ns frontend.test-parser
  (:require [cljs.test :refer-macros [is deftest testing]]
            [frontend.parser :as parser]
            [om.next :as om-next]))

(deftest flattening-parser-works
  (testing "reads"
    (testing "locally flatten by one level"
      (let [read (fn [{:keys [state]} k _]
                   {:value (get @state k)})
            p (parser/flattening-parser (om-next/parser {:read read}))]
        (is (= {:key-to-flatten {:a-root-key 123}}
               (p {:state (atom {:a-root-key 123})}
                  [{:key-to-flatten [:a-root-key]}])))))

    (testing "remotely keep the same structure"
      (let [read (fn [{:keys [state]} k _]
                   {:some-remote true})
            p (parser/flattening-parser (om-next/parser {:read read}))]
        (is (= [{:key-to-flatten [:a-root-key]}]
               (p {}
                  [{:key-to-flatten [:a-root-key]}]
                  :some-remote))))))

  (testing "mutations"
    (testing "locally delegate directly to the inner parser"
      (let [atom-to-change (atom 0)
            mutate (fn [{:keys [state]} key params]
                     {:value {:keys [:a-key]}
                      :action #(do
                                 (swap! atom-to-change inc)
                                 :a-return-value)})
            p (parser/flattening-parser (om-next/parser {:mutate mutate}))]
        (is (= {'some-mutation {:keys [:a-key]
                                :result :a-return-value}}
               (p {}
                  ['(some-mutation {:with "params"})])))
        (is (= 1 @atom-to-change))))

    (testing "locally pass errors back correctly as well"
      (let [mutate (fn [env key params]
                     {:value {:keys [:a-key]}
                      :action #(throw "an error")})
            p (parser/flattening-parser (om-next/parser {:mutate mutate}))]
        (is (= {'some-mutation {::om-next/error "an error"}}
               (p {}
                  ['(some-mutation {:with "params"})])))))

    (testing "remotely pass themselves along"
      (let [mutate (fn [{:keys [target]} key params]
                     {target true})
            p (parser/flattening-parser (om-next/parser {:mutate mutate}))]
        (is (= ['(some-mutation {:with "params"})]
               (p {}
                  ['(some-mutation {:with "params"})]
                  :some-remote))))

      (let [mutate (fn [{:keys [target]} key params] {})
            p (parser/flattening-parser (om-next/parser {:mutate mutate}))]
        (is (= []
               (p {}
                  ['(some-mutation {:with "params"})]
                  :some-remote)))))))
