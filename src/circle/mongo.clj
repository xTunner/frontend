(ns circle.mongo
  (:require [robert.hooke :as hook])
  (:require [clojure.string :as str])
  (:require [somnium.congomongo :as mongo])
  (:use [circle.util.map :only (map-keys)]))

(def warn-chars #{\? \!})

(defn to-db-translate [row]
  (map-keys (fn [col]
              (-> col (name) (str/replace #"-" "_") (keyword))) row))

(defn from-db-translate [row]
  (map-keys (fn [col]
              (-> col (name) (str/replace #"_" "-") (keyword))) row))

;; fetch-by-id and fetch-one are implemented in terms of fetch, so we only need to hook this.
(hook/add-hook mongo/fetch (fn [f & args]
                             (->> (apply f args)
                                  (map from-db-translate))))

;; (hook/add-hook mongo/insert! (fn [f & args]
;;                                (let [args (update-in (into [] args) [1] to-db-translate)]
;;                                  (apply f args))))

;; (hook/add-hook mongo/update! (fn [f & args]
;;                                (let [args (update-in (into [] args) [2] to-db-translate)]
;;                                  (apply f args))))




