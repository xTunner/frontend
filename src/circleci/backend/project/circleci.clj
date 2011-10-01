(ns circleci.backend.project.circleci
  (:require [circleci.backend.nodes :as nodes])
  (:require [circleci.backend.nodes.circleci :as circle])
  (:use [circleci.backend.build :only (build)])
  (:use [circleci.backend.action.vcs :only (checkout)])
  (:use [circleci.backend.action.bash :only (bash)])
  (:use [circleci.backend.action.junit :only (junit)]))

(def circle-build (build :project-name "CircleCI"
                         :build-num 1
                         :actions [(checkout "git@github.com:arohner/CircleCI.git")
                                   (bash [(lein deps)])
                                   (bash [(lein midje)])]
                         :group circle/circleci-group))

(def circle-deploy (build :project-name "CircleCI"
                          :type :deploy ;;?
                          :group circle/circleci-group
                          :actions [(checkout "git@github.com:arohner/CircleCI.git")
                                    (bash [(lein deps)
                                           (lein run)])
                                    ;;; Load Balancer
                                    ]))