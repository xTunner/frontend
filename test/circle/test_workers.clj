(ns circle.test-workers
  (:use midje.sweet)
  (:require circle.airbrake)
  (:use circle.workers))

(def div-by-zero (try (/ 1 0) (catch Exception e e)))

(fact "log-future returns futures"
  ;; A normal future will return a future object here, even if the
  ;; code obviously throws an exception. Make sure that's the case
  ;; here as well.
  (log-future (/ 1 0)) => future?)

(fact "log-future works"
  @(log-future (apply + [1 1])) => 2
  @(log-future (/ 1 0)) => (fn [m]
                             (let [e (get m "this Throwable was captured by midje:")]
                               (and (instance? Exception e) (= (class (.getCause e)) java.lang.ArithmeticException)))))

(fact "airbrake works"
  (against-background
    (circle.env/production?) => true)
  (circle.airbrake/airbrake :exception div-by-zero) => (contains {:id integer? :error-id integer? :url string?}))

(fact "log-future calls airbrake on failure"
  ;; the test here is that an expectation that airbrake is called.
  (against-background
    (circle.env/production?) => true)
  @(log-future (/ 1 0)) => (fn [m]
                             (let [e (get m "this Throwable was captured by midje:")]
                               (and (instance? Exception e) (= (class (.getCause e)) java.lang.ArithmeticException))))
  (provided
    (circle.airbrake/airbrake :data {:future true :body '(/ 1 0)} :exception (as-checker #(instance? java.lang.ArithmeticException %))) => nil))