(ns frontend.test-gencard
  (:require [cljs.test :refer-macros [is testing]]
            [clojure.spec :as s :include-macros true]
            [clojure.test.check.generators :as gen]
            [frontend.gencard :as gc]
            [om.core :as om]
            [om.next :as om-next :refer-macros [defui]])
  (:require-macros
   [devcards.core :as dc :refer [defcard deftest]]
   [sablono.core :refer [html]]))

(deftest signature-test
  (is (= {:type "div"
          "className" "foo"
          :children [{:type "span"
                      "className" "baz"
                      :children []}]}
         (gc/signature (html [:div {:class "foo" :title "bar"}
                           [:span {:class "baz" :title "qux"}
                            "Some text."]])))))

(s/def ::data-for-component
  (s/keys :req [::type ::description]))

(s/def ::type #{:type-a :type-b})

(s/def ::description (s/and string? seq))


(defn demo-child-component-om-prev [{:keys [description]} owner]
  (reify
    om/IRender
    (render [_]
      (html [:div description]))))

(defui DemoChildComponentOmNext
  Object
  (render [this]
    (let [{:keys [description]} (om-next/props this)]
      (html [:div description]))))

(def demo-child-component-om-next (om-next/factory DemoChildComponentOmNext))

(defn demo-component [props]
  (html
   [:div {:class (name (::type props))}
    [:.description
     (when (< 2 (count (::description props))) {:class "long"})
     (::description props)

     ;; Child components should be ignored.
     (om/build demo-child-component-om-prev {:description (::description props)})
     (demo-child-component-om-next {:description (::description props)})]]))

(defui DemoComponentOmNext
  Object
  (render [this]
    (demo-component (om-next/props this))))

(def demo-component-om-next (om-next/factory DemoComponentOmNext))

(deftest morph-data-test
  (testing "Generates one set of data for each morph of a functional component"
    (dotimes [_ 10]
      (let [data (gc/morph-data demo-component ::data-for-component)]
        (is (not (nil? data)))
        (is (every? (partial s/valid? ::data-for-component) data))
        (is (= 4 (count data)))
        (is (= 2 (count (group-by ::type data)))))))

  (testing "Generates one set of data for each morph of an Om component"
    (dotimes [_ 10]
      (let [data (gc/morph-data demo-component-om-next ::data-for-component)]
        (is (not (nil? data)))
        (is (every? (partial s/valid? ::data-for-component) data))
        (is (= 4 (count data)))
        (is (= 2 (count (group-by ::type data)))))))

  (testing "Returns nil when there are infinite morphs"
    (let [faulty-component #(html [:div {:class (::description %)}])]
      (is (nil? (gc/morph-data faulty-component ::data-for-component)))))

  (testing "Accepts generator overrides"
    (dotimes [_ 10]
      (let [data (gc/morph-data demo-component ::data-for-component {::description #(gen/return "Mostly harmless.")})]
        (is (every? (partial = "Mostly harmless.") (map ::description data)))))))

(defcard demo
  (html
   [:div
    [:style
     ".type-a { background-color: blue; }
      .type-b { background-color: green; }
      .long { color: red; }"]
    (let [data (gc/morph-data demo-component ::data-for-component)]
      (map demo-component data))]))
