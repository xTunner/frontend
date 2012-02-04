(ns circle.util.test-airbrake
  (:use circle.airbrake)
  (:use midje.sweet))

(def div-by-zero (try (/ 1 0) (catch Exception e e)))

(fact "airbrake works"
  (airbrake :exception div-by-zero :force true) => (contains {:id integer? :error-id integer? :url string?}))
