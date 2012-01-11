(ns circle.util.map
  "Fns for working with maps")

(defn map-keys
  "Calls f, a fn of one argument on each key in m. Replaces each key in m with (f k)"
  [f m]
  (->> m
       (map (fn [pair]
              [(f (key pair)) (val pair)]))
       (into {})))

(defn map-vals
  "Calls f, a fn of one arg on each value in m. Returns a new map with all the values that returned truthy"
  [f m]
  (->> m
       (map (fn [pair]
              [(key pair) (f (val pair))]))
       (into {})))

(defn filter-vals
  "Calls f, a fn of one arg on each value in m. Returns a new map with all the values that returned truthy"
  [f m]
  (->> m
       (filter (fn [pair]
                 (f (val pair))))
       (into {})))

(defn rename-keys
  "replace is a map of old keys to new keys. Replaces keys in m with new versions"
  [replace m]
  (map-keys (fn [k]
              (or (get replace k) k)) m))