(ns circle.backend.project.circle
  (:require [circle.backend.nodes :as nodes])
  (:require [circle.backend.nodes.circle :as circle])
  (:use [circle.backend.build :only (build)])
  (:use [circle.backend.action.vcs :only (checkout)])
  (:use [circle.backend.action.bash :only (bash)])
  ;; (:use [circle.backend.action.junit :only (junit)])
  )

(def circle-build (build :project-name "Circle"
                         :build-num 1
                         :actions [(checkout "git@github.com:arohner/CircleCI.git")
                                   (bash [(lein deps)])
                                   (bash [(lein midje)])]
                         :group circle/circle-group))

(def circle-deploy (build :project-name "Circle"
                          :build-num 1
                          :type :deploy
                          :group circle/circle-group
                          :actions [(checkout "git@github.com:arohner/CircleCI.git")
                                    (bash [(lein deps)
                                           (lein daemon start ":web")])
                                    ;;; Load Balancer
                                    ]))