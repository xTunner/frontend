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
    (with-redefs [segment/track-event (fn [event & [properties]]
                                        (swap! calls conj {:args (list event properties)}))]
      (f)
      @calls)))

(deftest track-default-works
  (let [click-event (first analytics/supported-click-and-impression-events)
        data {:view :a-view
              :user "foobar-user"
              :repo "foobar-repo"
              :org "foobar-org"}
        current-state (test-utils/state data)]
    (testing "track :default works with a current-state"
      (let [calls (stub-segment-track-event #(analytics/track {:event-type click-event
                                                               :current-state current-state}))]
        (is (= 1 (count calls)))
        (is (= click-event (-> calls first :args first)))
        (is (submap? data (-> calls first :args second)))))

    (testing "you need an event-type"
      (test-utils/fails-schema-validation #(analytics/track {:current-state current-state})))

    (testing "track :default is checking for valid event-data"
      (test-utils/fails-schema-validation #(analytics/track {:event-type click-event
                                                             :current-state current-state
                                                             :shibbity-doo-bot "heyooooo"})))

    (testing "track :default is not allowing non-valid events"
      (test-utils/fails-schema-validation #(analytics/track {:event-type :shibbity-ibbity-ima-fake-event
                                                             :current-state current-state})))))

(deftest track-external-click-works
  (testing "a valid external-click event is fired")
  (testing "track :external-click is checking for valid event-data")
  (testing "track :external-click is not allowing non-valid events")
  )

(deftest track-build-triggered-works
  (testing "track :build-triggered adds the correct properties")
  (testing "track :build-triggered is checking for valid event-data")
  )

(deftest track-view-build-works
  (testing "track :view-build adds the correct properties")
  (testing "track :view-build is checking for valid event-data")
  )

(deftest track-init-user-works
  (testing "track :init-user adds the correct properties and calls segment/identify")
  (testing "track :init-user is checking for valid event-data")
  )
