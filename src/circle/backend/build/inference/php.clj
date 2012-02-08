(ns circle.backend.build.inference.php
  (:use circle.util.fs)
  (:use [circle.backend.action.bash :only (bash)])
  (:use [circle.backend.action :only (defaction action)])
  (:require fs)
  (:require [circle.sh :as sh])
  (:require [circle.backend.build.inference :as inference]))

(defn path-contains-phpunit?
  []
  false)

(defn initialize-pear
  []
  "Intialize pear on the server."
  (bash (sh/q (pear config-set auto_discover 1))
        :name "pear initialization"
        :type :setup))

(defn install-phpunit
  "Action to install phpunit via pear."
  [repo]
  (bash (sh/q (pear install pear.phpunit.de/PHPUnit))
        :name "install phpunit"
        :type :setup))

(defn phpunit
  "Generate an action to run phpunit tests on a given directory."
  [test-dir]
  ;; HALP: Will this work? How do I get test-dir to be the value of the symbol
  ;; and not the symbol within the sh/q macro.
  (bash (sh/q phpunit ~test-dir)
        :name "PHPUnit"
        :type :test))

(defmethod inference/infer-actions* :php [_ repo]
  (let [path-to-phpunit "phpunit"]
    (->>
      [(initialize-pear)
       (when (not (path-contains-phpunit?))
         (install-phpunit repo))
       (phpunit (fs/join repo "test"))]
      (filter identity))))

