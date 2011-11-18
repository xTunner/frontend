(ns circle.util.map
  "Fns for working with maps")

(defn map-keys
  "Calls f, a fn of one argument on each key in m. Replaces each key in m with (f k)"
  [f m]
  (->> m
       (map (fn [pair]
              [(f (key pair)) (val pair)]))
       (into {})))

(defn rename-keys
  "replace is a map of old keys to new keys. Replaces keys in m with new versions"
  [replace m]
  (map-keys (fn [k]
              (or (get replace k) k)) m))