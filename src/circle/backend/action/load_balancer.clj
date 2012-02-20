(ns circle.backend.action.load-balancer
  (:import com.amazonaws.AmazonClientException)
  (:require [clj-http.client :as http])
  (:use [clojure.tools.logging :only (infof)])
  (:use [circle.backend.action :only (defaction abort!)])
  (:use [circle.util.except :only (throw-if-not)])
  (:require [clj-time.core :as time])
  (:use [circle.util.time :only (to-millis)])
  (:use [circle.model.build :only (build-log)])
  ;;(:use [robert.bruce :only (try-try-again)])
  (:use [circle.util.retry :only (wait-for)])
  (:use [circle.api.client.system :only (graceful-shutdown)])
  (:require [circle.backend.load-balancer :as lb])
  (:require [circle.backend.ec2 :as ec2])
  (:require [clojure.string :as str]))

(defaction add-instances []
  {:name "add to load balancer"}
  (fn [build]
    (try
      (let [lb-name (-> @build :lb-name)
            _ (throw-if-not lb-name "build LB name must not be null. Is it in your DB?")
            instance-ids (-> @build :instance-ids)
            _ (doseq [i instance-ids
                      :let [az (ec2/get-availability-zone i)]]
                (lb/ensure-availability-zone lb-name az))
            result (lb/add-instances lb-name instance-ids)]
        (build-log "added %s to load balancer %s successful" (str/join ", " instance-ids) lb-name))
      (catch AmazonClientException e
        (println "add-instances:" e)
        {:success false
         :continue false
         :err (.getMessage e)}))))

(defn lb-healthy-retries
  "Number of times to retry when waiting for the LB to become healthy. Defn so it can be rebound w/ midje"
  []
  10)

(defaction wait-for-healthy []
  {:name "wait for nodes LB healthy"}
  (fn [build]
    (let [instance-ids (-> @build :instance-ids)
          lb-name (-> @build :lb-name)]
      (try
        (wait-for
         {:sleep (to-millis (time/minutes 1))
          :tries (lb-healthy-retries)}
         #(lb/healthy? lb-name instance-ids))
        (build-log "instances %s all healthy in load balancer %s" instance-ids lb-name)
        (catch Exception e
          (abort! build (format "load balancer didn't report healthy for %s" instance-ids)))))))

(defn get-old-revisions
  "Returns all instance-ids attached to the load balancer that aren't tagged with current-rev"
  [lb-name current-rev]
  (let [lb-ids (set (lb/instance-ids lb-name))]
    (->>
     (ec2/describe-tags)
     (filter (fn [tag]
               (and (contains? lb-ids (-> tag :resourceId))
                    (= (-> tag :key) "rev")
                    (not= (-> tag :value) current-rev))))
     (map #(-> % :resourceId)))))

(defaction shutdown-remove-old-revisions []
  {:name "remove old revisions"}
  (fn [build]
    (try
      (let [lb-name (-> @build :lb-name)
            old-instances (get-old-revisions lb-name
                                             (-> @build :vcs_revision))
            terminated-instances (lb/terminated-instances lb-name)]
        (println "shutdown-remove-old:" old-instances)
        (when (seq old-instances)
          (lb/remove-instances lb-name old-instances)
          (doall (map #(graceful-shutdown (ec2/public-ip %)) old-instances)))
        (when (seq terminated-instances)
          (lb/remove-instances lb-name terminated-instances))
        {:success true})
      (catch AmazonClientException e
        {:success false
         :continue false
         :err (.getMessage e)}))))