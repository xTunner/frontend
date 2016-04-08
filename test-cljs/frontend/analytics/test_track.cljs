(ns frontend.analytics.test-track
  (:require [cljs.test :refer-macros [is deftest testing]]
            [bond.james :as bond :include-macros true]
            [frontend.state :as state]
            [frontend.controllers.controls :as controls]
            [frontend.analytics.core :as analytics]
            [frontend.analytics.track :as track]
            [frontend.analytics.segment :as segment]
            [frontend.analytics.test-utils :as analytics-utils]))

(deftest project-image-change-works
  (let [state (analytics-utils/current-state {})
        event :change-image-clicked]
    (testing "switching between precise and trusty with osx off sends the correct event"
      (bond/with-stub [segment/track-event]
        (let [data {:project-id "some-fake-org/some-fake-project"
                    :flag :trusty-beta
                    :value true}]
          (controls/post-control-event! "" :project-feature-flag-checked data state state)
          (is (= 1 (-> segment/track-event bond/calls count)))
          (is (analytics-utils/is-correct-arguments?
                (-> segment/track-event bond/calls first :args)
                event
                {:image "trusty"}))))

      (bond/with-stub [segment/track-event]
        (let [data {:project-id "some-fake-org/some-fake-project"
                    :flag :trusty-beta
                    :value false}]
          (controls/post-control-event! "" :project-feature-flag-checked data state state)
          (is (= 1 (-> segment/track-event bond/calls count)))
          (is (analytics-utils/is-correct-arguments?
                (-> segment/track-event bond/calls first :args)
                event
                {:image "precise"})))))

    (testing "switching on osx sets the image to be osx"
      (bond/with-stub [segment/track-event]
        (let [data {:project-id "some-fake-org/some-fake-project"
                    :flag :osx
                    :value true}]
          (controls/post-control-event! "" :project-feature-flag-checked data state state)
          (is (= 1 (-> segment/track-event bond/calls count)))
          (is (analytics-utils/is-correct-arguments?
                (-> segment/track-event bond/calls first :args)
                event
                {:image "osx"})))))

    (testing "switching off osx sets it to precise or trusty depending on what is enabled"
      (bond/with-stub [segment/track-event]
        (let [data {:project-id "some-fake-org/some-fake-project"
                    :flag :osx
                    :value false}
              state (assoc-in state (conj state/feature-flags-path :trusty-beta) false)]
          (controls/post-control-event! "" :project-feature-flag-checked data state state)
          (is (= 1 (-> segment/track-event bond/calls count)))
          (is (analytics-utils/is-correct-arguments?
                (-> segment/track-event bond/calls first :args)
                event
                {:image "precise"}))))

      (bond/with-stub [segment/track-event]
        (let [data {:project-id "some-fake-org/some-fake-project"
                    :flag :osx
                    :value false}
              state (assoc-in state (conj state/feature-flags-path :trusty-beta) true)]
          (controls/post-control-event! "" :project-feature-flag-checked data state state)
          (is (= 1 (-> segment/track-event bond/calls count)))
          (is (analytics-utils/is-correct-arguments?
                (-> segment/track-event bond/calls first :args)
                event
                {:image "trusty"})))))

    (testing "if there is nothing set in the state and the user turns osx off the image is nil"
      (bond/with-stub [segment/track-event]
        (let [data {:project-id "some-fake-org/some-fake-project"
                   :flag :osx
                   :value false}]
        (controls/post-control-event! "" :project-feature-flag-checked data state state)
        (is (= 1 (-> segment/track-event bond/calls count)))
        (is (analytics-utils/is-correct-arguments?
              (-> segment/track-event bond/calls first :args)
              event
              {:image nil})))))))
