(ns circle.backend.project.circle
  (:require [circle.backend.nodes.circle :as circle])
  (:use [circle.env :only (last-remote-commit)])
  (:use [circle.backend.build :only (build extend-group-with-revision)])
  (:use [circle.backend.action.nodes :only (start-nodes stop-nodes)])
  (:use [circle.backend.action.vcs :only (checkout)])
  (:use [circle.backend.action.bash :only (bash)])
  (:use [circle.util.model-validation :only (validate!)])
  (:use [circle.util.model-validation-helpers :only (is-map? require-predicate allow-keys)])
  (:use [arohner.utils :only (inspect)])
  (:require [circle.backend.action.load-balancer :as lb])
  (:require [clj-yaml.core :as yaml])
  (:require [clojure.contrib.io :as io])
  (:use [circle.backend.action.tag :only (tag-revision)])
  (:use [circle.util.core :only (apply-if)])
  (:use [midje.sweet])
  )

(defn circle-read-config-file
  []
  (-> "configs/circleci.yml"
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

(defn validate-action-map [cmd]
  (validate! [(is-map?)
              (require-predicate (fn [cmd]
                                   (= 1 (count cmd))))
              (fn [cmd]
                ((allow-keys [:environment :pwd] (format "only :environment and :pwd are allowed in action %s" cmd)) (-> cmd (first) (val))))] cmd))

(defn parse-action-map [cmd]
  (validate-action-map cmd)
  (let [body (-> cmd (first) (key))
        body (apply-if (keyword? body) name body)
        env (-> cmd (first) (val) :environment)
        pwd (-> cmd (first) (val) :pwd)]
    (bash body :environment env :pwd pwd)))

(fact "parse-action-map works"
  (let [cmd {(keyword "lein daemon start \":web\"") {:environment {:CIRCLE_ENV "production", :SWANK "true"}}}]
    (parse-action-map cmd) => truthy
    (provided
      (bash "lein daemon start \":web\"" :environment {:CIRCLE_ENV "production", :SWANK "true"} :pwd nil) => truthy :times 1)))

(defn parse-action [cmd]
  (cond
   (string? cmd) (bash cmd)
   (map? cmd) (parse-action-map cmd)))

(defn fetch-data [num name]
  "Return a build object by combining config files, db contents and defaults"
  (let [config (circle-read-config-file)
        actions (doall (map parse-action (-> config name :commands)))
        before (map #(apply % []) (-> known-actions name :prefix))
        after (map #(apply % []) (-> known-actions name :suffix))
        config-data {:build-num num
                     :actions (concat before actions after)
                     :notify-email (-> config name :notify-email)}]
    (merge config-data circle-db-data)))

(defn circle-build []
  (build (fetch-data 1 :build)))

(defn circle-deploy []
  (-> (build (fetch-data 2 :deploy))
      (extend-group-with-revision)))