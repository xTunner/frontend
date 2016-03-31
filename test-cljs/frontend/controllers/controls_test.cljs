(ns frontend.controllers.controls-test
  (:require [cemerick.cljs.test :as test]
            [frontend.controllers.controls :as controls]
            [bond.james :as bond])
  (:require-macros [cemerick.cljs.test :refer (is deftest testing)]))

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
