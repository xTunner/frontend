(ns circle.backend.build.utils
  (:require [circle.backend.build :as build]))

(defn minimal-build [& {:keys [actions]}]
  (build/build  {:project-name "test proj"
                 :build-num 1
                 :vcs-url "http://github.com/arohner/test-circle"
                 :actions (or actions [])}))