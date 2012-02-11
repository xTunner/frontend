(ns circle.test-mongo
  (:use [midje.sweet])
  (:require [somnium.congomongo :as mongo])
  (:require [circle.mongo :as c-mongo])
  (:require [circle.backend.build.test-utils :as test]))

(test/test-ns-setup)

(fact "ensure set works as planned"
  ;; add and check expected values
  (mongo/insert! :some_coll {:a "b" :c "d"})
  (let [old (mongo/fetch-one :some_coll)]
    (-> old :a) => "b"
    (-> old :c) => "d"

    ;; set and check expected values

    (let [result (c-mongo/set :some_coll (-> old :_id) :a "x" :e "f")
          count (mongo/fetch-count :some_coll)
          new (mongo/fetch-one :some_coll)]
      result => old
      (-> new :a) => "x"
      (-> new :c) => "d"
      (-> new :e) => "f"
      count => 1)))
