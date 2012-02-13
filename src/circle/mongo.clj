(ns circle.mongo
  (:require [robert.hooke :as hook])
  (:require [clojure.string :as str])
  (:require [somnium.congomongo :as mongo])
  (:require [circle.util.mongo :as mongo2])
  (:use [circle.util.core :only (defn-once)])
  (:use [circle.util.map :only (map-keys)]))

(def warn-chars #{\? \!})

(defn to-db-translate-name [col-name]
  (-> col-name (name) (str/replace #"-" "_") (keyword)))

(defn to-db-translate [row]
  (map-keys (fn [col]
              (let [new-col (to-db-translate-name col)]
                (when (and (not= col new-col) (not (nil? (get row new-col))))
                  (println "to-db-translate:" row "new-col" new-col "exists")
                  (assert false))

                new-col)) row))

(defn from-db-translate-name [col-name]
  (-> col-name (name) (str/replace #"^.+_" "-") (keyword)))

(defn from-db-translate [row]
  (println "from-db-translate:" row)
  (map-keys (fn [col]
              (let [new-col (from-db-translate-name col)]
                (assert (nil? (get row new-col)))
                new-col)) row))

;; TECHNICAL_DEBT: needs to/from-db-translate
(defn set-fields [coll id & {:as args}]
  (let [id (if (mongo2/object-id? id) id (mongo/object-id id))]
    (mongo/fetch-and-modify coll {:_id id} {:$set args})))



;; fetch-by-id and fetch-one are implemented in terms of fetch, so we only need to hook this.

(defn-once init
  (hook/add-hook #'mongo/fetch (fn [f & args]
                                 (let [[coll kw-args] args
                                       kw-args (apply hash-map kw-args)]
                                   (-> kw-args
                                       (update-in [:where] to-db-translate))
                                   )
                                 (->> (apply f args)
                                      (map from-db-translate))))

  (hook/add-hook #'mongo/insert! (fn [f & args]
                                   (let [args (update-in (into [] args) [1] to-db-translate)]
                                     (apply f args))))

  (hook/add-hook #'mongo/update! (fn [f & args]
                                   (let [args (update-in (into [] args) [2] to-db-translate)]
                                     (apply f args))))

  (hook/add-hook #'mongo/add-index! (fn [f & args]
                                      (let [args (update-in (into [] args) [1] #(map to-db-translate-name %))]
                                        (println "mongo/add-index" args)
                                        (apply f args)))))