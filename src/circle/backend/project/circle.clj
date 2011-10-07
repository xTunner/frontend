(ns circle.backend.project.circle
  (:require [circle.backend.nodes :as nodes])
  (:require [circle.backend.nodes.circle :as circle])
  (:use [circle.backend.build :only (build extend-group-with-revision)])
  (:use [circle.backend.action.nodes :only (start-nodes stop-nodes)])
  (:use [circle.backend.action.vcs :only (checkout)])
  (:use [circle.backend.action.bash :only (bash)])
  (:require [circle.backend.action.load-balancer :as lb])
  (:use [circle.backend.action.tag :only (tag-revision)]))

(def circle-build (build :project-name "Circle"
                         :build-num 1
                         :vcs-type :git
                         :vcs-url "git@github.com:arohner/CircleCI.git"
                         :vcs-revision "2c24ff2bba2fd07ea7128195b4193eb2f1b2453d"

                         :actions [(start-nodes)
                                   (checkout)
                                   (bash [(lein deps)])
                                   (bash [(lein midje)])
                                   (stop-nodes)]
                         :group circle/circle-group))

(def circle-deploy
  (extend-group-with-revision
   (build :project-name "Circle"
          :build-num 2
          :type :deploy
          :vcs-type :git
          :vcs-url "git@github.com:arohner/CircleCI.git"
          :vcs-revision "382279b1bd282f06a5aac1cbf5454cd7c997e45f"
          :num-nodes 1
          :lb-name "www"
          :group circle/circle-group
          :actions [(start-nodes)
                    (checkout)
                    (bash [(lein deps)
                           (export "CIRCLE_ENV=production")
                           (lein daemon start ":web")
                           (sudo "/etc/init.d/nginx" :start)])
                    (tag-revision)
                    (lb/add-instances)
                    (lb/wait-for-healthy)
                    (lb/shutdown-remove-old-revisions)])))