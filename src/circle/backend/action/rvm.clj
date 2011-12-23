(ns circle.backend.action.rvm
  "actions for dealing with RVM during builds"
  (:require [circle.backend.action :as action])
  (:use [circle.backend.build :only (checkout-dir)])
  (:require [circle.sh :as sh])
  (:use [circle.backend.action.bash :only (remote-bash-build bash)]))

(defn trust []
  (action/action
   :name "rvm trust"
   :act-fn (fn [build]
             (let [dir (checkout-dir build)]
               (remote-bash-build build (sh/q (rvm rvmrc trust ~dir)))))))

(defn rvm-use []
  (action/action
   :name "rvm use"
   :act-fn (fn [build]
             (let [dir (checkout-dir build)]
               (remote-bash-build build (sh/q (rvm use "1.9.2@circle-build" --default)))))))
