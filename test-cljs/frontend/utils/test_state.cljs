(ns frontend.utils.test-state
  (:require [cljs.test :refer-macros [is deftest testing use-fixtures]]
            [frontend.utils.state :as state-utils]))

(deftest test-adding-a-new-envvar
  (testing "adding a new envvar to an empty map gives just that envvar"
    (is (= {"a" 1}
           (state-utils/add-envvar-to-map
            {}
            {:name "a" :value 1}))))
  (testing "adding a new envvar to a map with an already existant entry overwrites"
    (is (= {"a" 1}
           (state-utils/add-envvar-to-map
            {"a" 2}
            {:name "a" :value 1})))))

(deftest test-turning-a-response-seq-to-a-map
  (testing "an 'empty' is an empty map"
    (is (= {}
           (state-utils/envvars-seq-to-map nil)))
    (is (= {}
           (state-utils/envvars-seq-to-map [])))
    (is (= {}
           (state-utils/envvars-seq-to-map '()))))
  (testing "many items come over into key value pairs"
    (is (= {"foo" "xxxxcvbn"
            "test" "xxxx456"}
           (state-utils/envvars-seq-to-map
            [{:name "foo", :value "xxxxcvbn"} {:name "test", :value "xxxx456"}])))))

(deftest test-seq-to-map_goes-to_map-to-seq
  (testing "many items come over into key value pairs"
    (let [input [{:name "foo", :value "xxxxcvbn"} {:name "test", :value "xxxx456"}]]
      (is (= input
             (state-utils/envvars-map-to-seq
              (state-utils/envvars-seq-to-map
               input)))))))
