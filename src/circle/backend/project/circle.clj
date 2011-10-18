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
                         :vcs-revision "a2ec5dbe2d402dc926e1e8b803d4fc6738562b5c"

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
          :vcs-revision "5c4c9b4104032b9b443cefe5d178c3a5fd46ce91"
          :aws-credentials circle.aws-credentials/AWS-access-credentials
          :r53-zone-id "ZBVDNEPFXWZR2"
          :num-nodes 1
          :lb-name "www"
          :group circle/circle-group
          :actions [(start-nodes)
                    (checkout)
                    (bash [(lein deps)])
                    (bash [(export "CIRCLE_ENV=production")
                           (export "SWANK=true")
                           (lein daemon start ":web")])
                    (bash [(sudo "/etc/init.d/nginx" :start)])
                    (tag-revision)
                    (lb/add-instances)
                    (lb/wait-for-healthy)
                    (lb/shutdown-remove-old-revisions)])))