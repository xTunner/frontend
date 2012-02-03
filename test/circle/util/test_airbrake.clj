(ns circle.util.test-airbrake
  (:use circle.airbrake)
  (:use midje.sweet))

(def div-by-zero (try (/ 1 0) (catch Exception e e)))

(fact "airbrake works"
  (circle.airbrake/airbrake :exception div-by-zero) => (contains {:id integer? :error-id integer? :url string?})
    (provided
      (circle.env/env) => :production))
