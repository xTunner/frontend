(ns circle.backend.build.utils
  (:require [circle.backend.build :as build]))

(defn minimal-build [& {:keys [project-name
                               actions
                               notify-email
                               repository
                               commits]}]
  
  (build/build {:project-name (or project-name "test proj")
                :build-num 1
                :vcs-url "http://github.com/arohner/test-circle"
                :notify-email (or notify-email [])
                :actions (or actions [])
                :repository repository
                :commits commits}))