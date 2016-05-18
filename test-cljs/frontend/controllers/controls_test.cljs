(ns frontend.controllers.controls-test
  (:require [cljs.core.async :refer [chan <! >!]]
            [cljs.test :as test]
            [frontend.utils.ajax :as ajax]
            [frontend.analytics.core :as analytics]
            [frontend.controllers.controls :as controls]
            [bond.james :as bond :include-macros true])
  (:require-macros [cljs.test :refer [is deftest testing async]]
                   [cljs.core.async.macros :refer [go]]))

(deftest extract-from-works
  (is (= nil (controls/extract-from nil nil)))
  (is (= nil (controls/extract-from nil [])))
  (is (= nil (controls/extract-from nil [:a])))
  (is (= nil (controls/extract-from {} [:a])))
  (is (= {:a 1} (controls/extract-from {:a 1} [:a])))
  (is (= {:a {:b {:c 1}}} (controls/extract-from {:a {:b {:c 1}}, :d 2} [:a :b]))))

(deftest merge-settings-works
  (testing "nil paths always return settings"
    (let [settings {:a 1, :b {:c 3}}]
      (is (= settings (controls/merge-settings nil nil settings)))
      (is (= settings (controls/merge-settings nil {} settings)))
      (is (= settings (controls/merge-settings nil {:a 4} settings)))))

  (testing "empty paths always return settings"
    (let [settings {:a 1, :b {:c 3}}]
      (is (= settings (controls/merge-settings [] nil settings)))
      (is (= settings (controls/merge-settings [] {} settings)))
      (is (= settings (controls/merge-settings [] {:a 4} settings)))))

  (testing "nil settings returns project values"
    (let [project {:a 1, :b {:c 3}}]
      (is (= {:b {:c 3}} (controls/merge-settings [[:b :c]], project, nil)))
      (is (= {:b {:c 3}} (controls/merge-settings [[:b] [:b :c]], project, nil)))
      (is (= {} (controls/merge-settings [[:a]], {}, nil)))))

  (testing "nil project always return settings"
    (is (= {:a 1} (controls/merge-settings [[:b]], nil, {:a 1})))
    (is (= {:a 1, :b {:c 4}} (controls/merge-settings [[:b :c]], nil, {:a 1, :b {:c 4}}))))

  (testing "empty project always return settings"
    (is (= {:a 1} (controls/merge-settings [[:b]], {}, {:a 1})))
    (is (= {:a 1, :b {:c 4}} (controls/merge-settings [[:b :c]], {}, {:a 1, :b {:c 4}}))))

  (testing "empty settings use pathed portions of project data"
    (is (= {} (controls/merge-settings [[:b]], {}, {})))
    (is (= {} (controls/merge-settings [[:a :b :c]], {:d 1}, {})))
    (is (= {:b {:c 4}} (controls/merge-settings [[:b :c]], {:a 1, :b {:c 4}}, {})))
    (is (= {:b {:c 4}, :d 5} (controls/merge-settings [[:b :c], [:d]], {:a 1, :b {:c 4}, :d 5}, {}))))

  (testing "top-level settings merge correctly"
    (is (= {:a 1, :b 2, :c 3} (controls/merge-settings [[:a], [:b]], {}, {:a 1, :b 2, :c 3})))
    (is (= {:a 10, :b 3, :c 2} (controls/merge-settings [[:a], [:b]], {:a 1, :b 3}, {:a 10, :c 2})))
    (is (= {:a 10, :b {:c 2}} (controls/merge-settings [[:a], [:b :c]], {:a 1, :b 3}, {:a 10, :b {:c 2}}))))

  (testing "nested settings merge correctly"
    (is (= {:e 2} (controls/merge-settings [[:a :b :c]], {:d 1}, {:e 2})))
    (is (= {:a {:b {:c 2}}} (controls/merge-settings [[:a :b]], {:a {:x 10}}, {:a {:b {:c 2}}})))
    (is (= {:a {:b {:c 2}}} (controls/merge-settings [[:a]], {:a {:b {:c 1}}}, {:a {:b {:c 2}}})))
    (is (= {:a {:b {:c 2}}} (controls/merge-settings [[:a :b]], {:a {:b {:c 1}}}, {:a {:b {:c 2}}})))
    (is (= {:a {:b {:c 2}}} (controls/merge-settings [[:a :b :c]], {:a {:b {:c 1}}}, {:a {:b {:c 2}}})))

    (is (= {:a {:b {:c 1, :d 2}}} (controls/merge-settings [[:a :b :c]], {:a {:b {:c 1}}}, {:a {:b {:d 2}}})))))

(defn success-event [] "success")

(defn failed-event [] "failed")

(defn check-button-ajax [{:keys [status success-count failed-count]}]
  (async done
         (go
           (bond/with-spy [success-event failed-event]
             (bond/with-stub [[ajax/ajax (fn [_ _ _ c _] (go (>! c  ["" status ""])))]]
               (let [channel (chan)]
                 (controls/button-ajax "a" "b" "c" channel
                                       :fake "data"
                                       :events {:success success-event
                                                :failed failed-event})
                 (<! channel)
                 (is (= success-count (-> success-event bond/calls count)))
                 (is (= failed-count (-> failed-event bond/calls count)))
                 (done)))))))

(deftest button-ajax-failed-event-works
  (testing "button-ajax correctly sends a failed event on failed"
    (check-button-ajax {:status :success
                        :success-count 1
                        :failed-count 0})))

(deftest button-ajax-success-event-works
  (testing "button-ajax correctly sends a success event on success"
    (check-button-ajax {:status :failed
                        :success-count 0
                        :failed-count 1})))

(deftest button-ajax-random-status-no-event-fires
  (testing "a non success or failed event fire no events"
    (check-button-ajax {:status :foobar
                        :success-count 0
                        :failed-count 0})))

(deftest post-control-event-activate-plan-trial-works
  (let [analytics-calls (atom [])
        org-name "foo"
        vcs-type "github"
        plan-type :paid
        template :t3
        controller-data {:plan-type plan-type
                         :template template
                         :org {:name org-name :vcs_type vcs-type}}
        current-state {:zippity "doo-da"}]
    (with-redefs [analytics/track (fn [event-data]
                                    (swap! analytics-calls conj {:args (list event-data)}))]
      (controls/post-control-event! {} :activate-plan-trial controller-data {} current-state)

      (testing "the post-control-event activate-plan-trial sends a :start-trial-clicked event with the correct properties"
        (is (= (count @analytics-calls) 1))
        (let [args (-> @analytics-calls first :args first)]
          (is (= (:event-type args) :start-trial-clicked))
          (is (= (:current-state args) current-state))
          (is (= (:properties args) {:org org-name
                                     :vcs-type vcs-type
                                     :plan-type :linux
                                     :template template})))))))
