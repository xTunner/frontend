(ns frontend.analytics.test-core
  (:require [cljs.test :refer-macros [is deftest testing use-fixtures]]
            [schema.test]
            [frontend.utils.seq :refer [submap?]]
            [frontend.test-utils :as test-utils]
            [frontend.analytics.core :as analytics]
            [frontend.analytics.segment :as segment]))

(use-fixtures :once schema.test/validate-schemas)

(defn stub-segment-track-event
  "Given a function, call it and return the args segment/track-event
  was passed."
  [f]
  (let [calls (atom [])]
    (with-redefs [segment/track-event (fn [& args] (swap! calls conj {:args args}))]
      (f)
      @calls)))

(deftest track-default-works
  (let [click-event (first analytics/supported-click-and-impression-events)
        data {:view :a-view
              :user "foobar-user"
              :repo "foobar-repo"
              :org "foobar-org"}]
    (testing "track :default works with a owner"
      (let [calls (stub-segment-track-event #(analytics/track {:event-type click-event
                                                               :owner (test-utils/owner data)}))]
        (is (= 1 (count calls)))
        (is (= click-event (-> calls first :args first)))
        (is (submap? data (-> calls first :args second)))))

    (testing "track :default works with a current-state"
      (let [calls (stub-segment-track-event #(analytics/track {:event-type click-event
                                                               :current-state (test-utils/current-state data)}))]
        (is (= 1 (count calls)))
        (is (= click-event (-> calls first :args first)))
        (is (submap? data (-> calls first :args second)))))

    (testing "you can't call track with a owner and a current-state"
      (test-utils/fails-schema-validation #(analytics/track {:event-type click-event
                                                             :owner (test-utils/owner data)
                                                             :current-state (test-utils/current-state data)})))

    (testing "track :default is checking for valid event-data"
      (test-utils/fails-schema-validation #(analytics/track {:event-type click-event
                                                             :owner (test-utils/owner data)
                                                             :shibbity-doo-bot "heyooooo"})))

    (testing "track :default is not allowing non-valid events"
      (test-utils/fails-schema-validation #(analytics/track {:event-type :shibbity-ibbity-ima-fake-event
                                                             :owner (test-utils/owner data)})))))

