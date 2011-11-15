(ns circle.backend.project.circle
  (:require [circle.backend.action.load-balancer :as lb])
  (:require [circle.backend.build :as build])
  (:require [circle.backend.nodes.circle :as circle])
  (:require [clj-yaml.core :as yaml])
  (:require [clojure.contrib.io :as io])
  (:require fs)
  (:use [arohner.utils :only (inspect)])
  (:use [circle.backend.action.bash :only (bash)])
  (:use [circle.backend.action.nodes :only (start-nodes stop-nodes)])
  (:use [circle.backend.action.tag :only (tag-revision)])
  (:use [circle.backend.action.vcs :only (checkout)])
  (:use [circle.env :only (last-remote-commit)])
  (:use [circle.util.core :only (apply-if)])
  (:use [circle.util.model-validation :only (validate!)])
  (:use [circle.util.model-validation-helpers :only (is-map? require-predicate allow-keys)])
  (:use [midje.sweet]))

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

(defn parse-action-map [build cmd]
  (validate-action-map cmd)
  (let [body (-> cmd (first) (key))
        body (apply-if (keyword? body) name body)
        env (-> cmd (first) (val) :environment)
        pwd (-> cmd (first) (val) :pwd)
        final-pwd (if pwd
                    (fs/join (build/checkout-dir build) pwd)
                    (build/checkout-dir build))]
    (bash body :environment env :pwd final-pwd)))

(defn parse-action [build cmd]
  (cond
   (string? cmd) (bash cmd)
   (map? cmd) (parse-action-map build cmd)))

(defn parse-notify [notifies]
  (for [n notifies]
    (if (re-find #"^:" n)
      (keyword (.substring n 1))
      n)))

(defn fetch-data [num name]
  "Return a build object by combining config files, db contents and defaults"
  (let [config (circle-read-config-file)
        b (build/build (merge
                        {:notify-email (-> config name :notify-email (parse-notify))
                         :build-num num
                         :actions []}
                        circle-db-data))
        actions (doall (map (partial parse-action b) (-> config name :commands)))
        before (map #(apply % []) (-> known-actions name :prefix))
        after (map #(apply % []) (-> known-actions name :suffix))]
    (dosync
     (alter b assoc :actions (concat before actions after)))
    b))

(defn circle-build []
  (fetch-data 1 :build))

(defn circle-deploy []
  (-> (fetch-data 2 :deploy)
      (build/extend-group-with-revision)))

