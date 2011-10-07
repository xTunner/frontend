(ns circle.backend.action.load-balancer
  (:require [circle.backend.nodes :as nodes])
  (:require [circle.backend.action :as action])
  (:require [circle.backend.load-balancer :as lb])
  (:require [circle.backend.ec2 :as ec2])
  (:require [circle.backend.ec2-tag :as tag]))

(defn add-instances []
  (action/action
   :name "add to load balancer"
   :act-fn (fn [context]
             (try
               (let [result (lb/add-instances (-> context :build :lb-name)
                                              (nodes/group-instance-ids (-> context :build :group)))]
                 {:success true})
               (catch Exception e
                 {:success false
                  :continue false})))))

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

(defn wait-for-healthy []
  :name "wait for nodes LB healthy"
  :act-fn (fn [context]
            (if (wait-for-healthy* (-> context :build :lb-name)
                               :instance-ids (nodes/group-instance-ids (-> context :build :group))
                               :sleep 5
                               :retries 10)
              {:success true}
              {:success false
               :continue false})))

(defn get-old-revisions [lb-name current-rev]
  (let [lb-ids (set (lb/instance-ids lb-name))]
    (filter (fn [tag]
              (and (contains? lb-ids (-> tag :resourceId))
                   (= (-> tag :key) :rev)
                   (not= (-> tag :value) current-rev))) (tag/describe-tags))))

(defn shutdown-remove-old-revisions
  "remove from the LB and shutdown  all instances that don't have the revision tag of the current deploy"
  []
  :name "remove old revisions"
  :act-fn (fn [context]
            (try
              (let [lb-name (-> context :build :lb-name)
                    old-instances (get-old-revisions lb-name
                                                     (-> context :build :vcs-revision))]
                (lb/remove-instances lb-name old-instances)
                (ec2/terminate-instances old-instances)
                {:success true})
              (catch Exception e
                {:success false
                 :continue false}))))