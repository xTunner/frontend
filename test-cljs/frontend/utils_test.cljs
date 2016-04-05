(ns frontend.utils-test
  (:require [cljs.test :as test :refer-macros [deftest is testing]]
            [frontend.utils :as utils]
            [frontend.utils.github :as gh-utils]))


(deftest deep-merge-works
  (testing "one-level"
    (is (= {:a 1 :b 2} (utils/deep-merge {:a 1} {:b 2})))
    (is (= {:a 2} (utils/deep-merge {:a 1} {:a 2})))
    (is (= {:a 1} (utils/deep-merge nil {:a 1})))
    (is (= {:a 1} (utils/deep-merge {:a 1} nil)))
    (is (thrown? js/Error (utils/deep-merge {:a 1} [1 2])))
    (is (thrown? js/Error (utils/deep-merge [1 2] {:a 1}))))

  (testing "nested maps"
    (is (= {:a {:b 1, :c 2}} (utils/deep-merge {:a {:b 1}} {:a {:c 2}})))
    (is (= {:a {:b {:c 2}}} (utils/deep-merge {:a {:b 1}} {:a {:b {:c 2}}})))
    (is (= {:a {:b {:e 2, :c 15}, :f 3}}
           (utils/deep-merge {:a {:b {:c 1}}}
                             {:a {:b {:c 15 :e 2} :f 3}}))))

  (testing "maps with other data-structures"
    (is (= {:a [1]} (utils/deep-merge {:a {:b 2}} {:a [1]})))
    (is (= {:a {:b 2}} (utils/deep-merge {:a [1]} {:a {:b 2}}))))

  (testing "explicit nils in later maps override earlier values"
    (is (= {:a nil} (utils/deep-merge {:a 1} {:a nil})))
    (is (= {:a {:b nil, :d {:e 2, :f 3}}}
           (utils/deep-merge {:a {:b {:c 1} :d {:e 2}}}
                             {:a {:b nil :d {:f 3}}})))))

(deftest avatar-url
  (testing "typical input"
    (is (contains? #{"https://avatars0.githubusercontent.com/u/1551784?v=2&s=200"
                     "https://avatars0.githubusercontent.com/u/1551784?s=200&v=2"}
                   (gh-utils/make-avatar-url {:avatar_url "https://avatars0.githubusercontent.com/u/1551784?v=2"
                                              :login "someuser"
                                              :gravatar_id "somegravatarid"}))))

  (testing "specified size"
    (is (contains? #{"http://example.com?v=2&s=17"
                     "http://example.com?s=17&v=2"}
           (gh-utils/make-avatar-url {:avatar_url "http://example.com?v=2"} :size 17))))

  (testing "without parameters"
    (is (= "http://example.com?s=200"
           (gh-utils/make-avatar-url {:avatar_url "http://example.com"}))))
 
  (testing "fall back to gravatar/identicon"
    (is (contains? #{"https://secure.gravatar.com/avatar/bar?d=https%3A%2F%2Fidenticons.github.com%2Ffoo.png&s=200"
                     "https://secure.gravatar.com/avatar/bar?s=200&d=https%3A%2F%2Fidenticons.github.com%2Ffoo.png"}
                   (gh-utils/make-avatar-url {:login "foo" :gravatar_id "bar"})))))


(deftest split-map-values-at-works
  (let [orig (into (sorted-map) [[:a [1 2 3]], [:b [4 5 6]]])]
    (testing "split spill over to bottom"
      (is (= [{:a [1 2]} {:a [3] :b [4 5 6]}]
             (utils/split-map-values-at orig 2))))
    (testing "even split"
      (is (= [{:a [1 2 3]} {:b [4 5 6]}]
             (utils/split-map-values-at orig 3))))))
