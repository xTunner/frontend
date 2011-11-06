(ns circle.backend.project.circle
  (:require [circle.backend.nodes.circle :as circle])
  (:use [circle.env :only (last-remote-commit)])
  (:use [circle.backend.build :only (build extend-group-with-revision)])
  (:use [circle.backend.action.nodes :only (start-nodes stop-nodes)])
  (:use [circle.backend.action.vcs :only (checkout)])
  (:use [circle.backend.action.bash :only (bash)])
  (:require [circle.backend.action.load-balancer :as lb])
  (:require [clj-yaml.core :as yaml])
  (:require [clojure.contrib.io :as io])
  (:use [circle.backend.action.tag :only (tag-revision)]))

(defn circle-read-config-file
  []
  (-> "configs/circle.yml"
      io/reader
      yaml/parse-string))

;; The info we should have in the database
(def circle-db-data {:project-name "Circle"
                     :vcs-url "git@github.com:arohner/CircleCI.git"
                     :vcs-revision (last-remote-commit)
                     :aws-credentials circle.aws-credentials/AWS-access-credentials
                     :r53-zone-id "ZBVDNEPFXWZR2"
                     :num-nodes 1
                     :lb-name "www"
                     :group circle/circle-group})

(def known-actions {:build {:prefix [start-nodes checkout]
                            :suffix [stop-nodes]}
                    
                    :deploy {:prefix [start-nodes checkout]
                             :suffix [tag-revision
                                      lb/add-instances
                                      lb/wait-for-healthy
                                      lb/shutdown-remove-old-revisions]}})

(defn fetch-data [num name]
  "Return a build object by combining config files, db contents and defaults"
  (let [config (circle-read-config-file)
        actions (map #(bash [%]) (-> config name :commands))
        before (-> known-actions name :prefix)
        after (-> known-actions name :suffix)
        config-data {:build-num num
                     :actions (concat before actions after)}]
    (merge config-data circle-db-data)))


(defn circle-build []
  (build (fetch-data 1 :build)))

(defn circle-deploy []
  (-> (build (fetch-data 2 :deploy))
      (extend-group-with-revision)))













