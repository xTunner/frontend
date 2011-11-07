(ns circle.backend.action.load-balancer
  (:import com.amazonaws.AmazonClientException)
  (:require [circle.backend.nodes :as nodes])
  (:use [circle.backend.action :only (defaction)])
  (:require [circle.backend.load-balancer :as lb])
  (:require [circle.backend.ec2 :as ec2])
  (:require [circle.backend.ec2-tag :as tag]))

(defaction add-instances []
  {:name "add to load balancer"}
  (fn [build]
    (try
      (let [lb-name (-> @build :lb-name)
            instance-ids (-> @build :instance-ids)
            _ (doseq [i instance-ids
                      :let [az (ec2/get-availability-zone i)]]
                (lb/ensure-availability-zone lb-name az))
            result (lb/add-instances lb-name instance-ids)]
        {:success true
         :out (format "add" instance-ids "to load balancer" lb-name "successful")})
      (catch AmazonClientException e
        (println "add-instances:" e)
        {:success false
         :continue false
         :err (.getMessage e)}))))

(defn wait-for-healthy* [lb-name & {:keys [instance-ids 
                                           retries ;; number of times to retry
                                           sleep ;; how long to sleep between attempt, seconds
                                           ]}]
  (loop [retries retries]
    (if (> retries 0)
      (if (apply lb/healthy? lb-name instance-ids)
        true
        (do
          (Thread/sleep (* 1000 sleep))
          (recur (dec retries))))
      false)))

(defaction wait-for-healthy []
  {:name "wait for nodes LB healthy"}
  (fn [build]
    (let [instance-ids (-> @build :instance-ids)]
      (if (wait-for-healthy* (-> @build :lb-name)
                             :instance-ids instance-ids
                             :sleep 10
                             :retries 10)
        {:success true
         :out (str instance-ids "are all healthy")}
        {:success false
         :continue false}))))

(defn get-old-revisions
  "Returns all instance-ids attached to the load balancer that aren't tagged with current-rev"
  [lb-name current-rev]
  (let [lb-ids (set (lb/instance-ids lb-name))]
    (filter (fn [tag]
              (and (contains? lb-ids (-> tag :resourceId))
                   (= (-> tag :key) :rev)
                   (not= (-> tag :value) current-rev))) (tag/describe-tags))))

(defaction shutdown-remove-old-revisions []
  {:name "remove old revisions"}
  (fn [build]
    (try
      (let [lb-name (-> @build :lb-name)
            old-instances (get-old-revisions lb-name
                                             (-> @build :vcs-revision))
            terminated-instances (lb/terminated-instances lb-name)]
        (println "shutdown-remove-old:" old-instances)
        (when (seq old-instances)
          (lb/remove-instances lb-name old-instances)
          (apply ec2/terminate-instances! old-instances))
        (when (seq terminated-instances)
          (lb/remove-instances lb-name terminated-instances))
        {:success true})
      (catch AmazonClientException e
        {:success false
         :continue false
         :err (.getMessage e)}))))