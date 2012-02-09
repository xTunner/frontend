(ns circle.backend.build.inference.php
  (:use circle.util.fs)
  (:use [circle.backend.action.bash :only (bash)])
  (:use [circle.backend.action :only (defaction action)])
  (:require fs)
  (:require [circle.sh :as sh]))

(defn phpunit
  "Generate an action to run phpunit tests on a given directory. First checks
  the repo for a vendorized phpunit, otherwise assumes phpunit will be in $PATH"
  [repo test-dir]
  (let [path-to-phpunit (or (first (files-matching
                                     repo
                                     #"phpunit(\.php)?$"))
                               'phpunit)]
    (bash (sh/q ~path-to-phpunit ~test-dir)
            :name "Run PHPUnit"
            :type :test)))

;; TODO: extend this to check for a vendorized php unit and use it if present.
(defn spec
  "Run phpunit on the test directory."
  [_ repo]
  (->>
    [(phpunit repo (fs/join repo "test"))]
    (filter identity)))




