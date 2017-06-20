(ns frontend.test-gencard
  (:require [cljs.test :refer-macros [is]]
            [clojure.spec :as s :include-macros true]
            [clojure.test.check.generators :as gen]
            [goog.object :as gobject])
  (:require-macros
   [devcards.core :as dc :refer [defcard deftest]]
   [sablono.core :refer [html]]))

(def ignored-props #{"title"})


(defn signature [element]
  (let [props (gobject/get element "props")
        children (->> (gobject/get props "children")
                      js/React.Children.toArray
                      (filter js/React.isValidElement))]
    (into {:type (gobject/get element "type")
           :children (map signature children)}
          (map (juxt identity (partial gobject/get props))
               (remove (conj ignored-props "children")
                       (gobject/getKeys props))))))

(defn morph-data [component spec]
  (->> (gen/sample-seq (s/gen spec))
       (take 100)
       (group-by (comp signature component))
       (sort-by (comp hash key))
       vals
       (map first)))



(s/def ::data-for-component
  (s/keys :req [::type ::description]))

(s/def ::type #{:type-a :type-b})

(s/def ::description (s/and string? seq))

(defn component [props]
  (html
   [:div {:class (name (::type props))}
    [:.description
     (when (< 2 (count (::description props))) {:class "long"})
     (::description props)]]))

(deftest signature-test
  (is (= {:type "div"
          "className" "foo"
          :children [{:type "span"
                      "className" "baz"
                      :children []}]}
         (signature (html [:div {:class "foo" :title "bar"}
                           [:span {:class "baz" :title "qux"}
                            "Some text."]])))))

(deftest morph-data-test
  (dotimes [_ 10]
    (let [data (morph-data component ::data-for-component)]
      (is (every? (partial s/valid? ::data-for-component) data))
      (is (= 4 (count data)))
      (is (= 2 (count (group-by ::type data)))))))

(defcard demo
  (html
   [:div
    [:style
     ".type-a { background-color: blue; }
      .type-b { background-color: green; }
      .long { color: red; }"]
    (let [data (morph-data component ::data-for-component)]
      (map component data))]))
