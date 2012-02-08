(ns circle.backend.build.inference.php
  (:use circle.util.fs)
  (:use [circle.backend.action.bash :only (bash)])
  (:use [circle.backend.action :only (defaction action)])
  (:require fs)
  (:require [circle.sh :as sh])
  (:require [circle.backend.build.inference :as inference]))

(defn phpunit
  "Generate an action to run phpunit tests on a given directory."
  [test-dir]
  (bash (sh/q phpunit ~test-dir)
        :name "PHPUnit"
        :type :test))

;; TODO: extend this to check for a vendorized php unit and use it if present.
(defmethod inference/infer-actions* :php [_ repo]
  "Run phpunit on the test directory."
  (->>
    [(phpunit (fs/join repo "test"))]
    (filter identity)))

