(ns frontend.components.test-integrations
  (:require [clojure.string :as s])
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var)]))


(deftest test-iframe-attrs-not-supported-by-react
  (let [[major minor patch] (map js/parseInt (s/split (.-version js/React) #"\."))]
    (is (or (> 0 major)
            (and (= 0 major)
                 (or (> 13 minor)
                     (and (= 13 minor)
                          (>= 1 patch)))))
        "Now that we're on React >= 0.13.1, we can use :allowfullscreen as a DOM attribute. See TODO in frontend.components.integrations.")))
