(ns circle.karma
  (:require [clojure.string :as string]
            [cemerick.cljs.test :as test]))

(defn get-total-test-count []
  (reduce + (map count (vals @test/registered-tests))))

(defn run-karma-test! [test-name test]
  (let [log-output (atom '())
        start (.getTime (js/Date.))]
    (test/set-print-fn! (fn [x] (swap! log-output conj x)))
    (let
        ;; [{:result result :time time} (run-test-with-time test)]
        [result (test/test-function test)]
      (.result js/__karma__
               (clj->js
                { "id" ""
                  "description" (-> test-name name)
                  "suite" [(-> test-name namespace str)]
                  "success" (and (zero? (:error result))
                                 (zero? (:fail result)))
                  "skipped" nil
                  "time" (- (.getTime (js/Date.)) start)
                  "log" [(string/join "\n" (reverse @log-output))]})))))

(defn ^:export run-tests-for-karma []
  (do (.info js/__karma__ (clj->js {:total (get-total-test-count)}))
      (doseq [[ns ns-tests] @test/registered-tests ]
        (doseq [[test-name test] ns-tests]
          (run-karma-test! test-name test)))
      (.complete js/__karma__ (clj->js {}))))
