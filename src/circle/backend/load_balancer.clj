(ns circle.backend.load-balancer
  (:import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient
           com.amazonaws.services.ec2.AmazonEC2Client
           (com.amazonaws.services.elasticloadbalancing.model
            ConfigureHealthCheckRequest
            CreateLoadBalancerRequest
            DeleteLoadBalancerRequest
            DeregisterInstancesFromLoadBalancerRequest
            DescribeInstanceHealthRequest
            DescribeLoadBalancersRequest
            EnableAvailabilityZonesForLoadBalancerRequest
            HealthCheck
            Instance
            Listener
            RegisterInstancesWithLoadBalancerRequest))
  (:use [circle.aws-credentials :only (aws-credentials)])
  (:use [pallet.thread-expr :only (when->)])
  (:use [circle.utils.except :only (throw-if-not)])
  (:use [circle.utils.core :only (apply-map)]))

(defmacro with-elb-client
  [client & body]
  `(let [~client (AmazonElasticLoadBalancingClient. aws-credentials)]
     ~@body))

(defn delete-balancer
  "name is the name passed to create-balancer. Returns nil"
  [name]
  (with-elb-client client
    (.deleteLoadBalancer client (DeleteLoadBalancerRequest. name))))

(defn add-instances
  "Instances is a seq of strings, each an EC2 instance id"
  [lb-name instances]
  (with-elb-client client
    (-> client
        (.registerInstancesWithLoadBalancer
         (RegisterInstancesWithLoadBalancerRequest. lb-name
                                                (for [i instances]
                                                  (Instance. i))))
        (.getInstances)
        (->>
         (map bean)))))

(defn remove-instances
  [lb-name instances]
  (with-elb-client client
    (.deregisterInstancesFromLoadBalancer
     client
     (DeregisterInstancesFromLoadBalancerRequest. lb-name
                                                (for [i instances]
                                                  (Instance. i))))))

(defn configure-health-check
  "Instructs the load balancer how to determine when the EC2 instances are healthy.

   target - what to ping. in the format 'protocol:port' Protocol can be one of TCP, HTTP, HTTPS, or SSL. When the protocol is HTTP(S), should be in the format 'HTTP:80/path/to/request'. Any status other than 200 is considered unhealthy.
   interval - how often to ping, in seconds
   timeout - how long to wait before a ping fails.
   unhealthy-threshold - number of failed pings before the instance is marked unhealthy and disabled from the load balancer.
   healthy-threshold - number of successful pings before the instance is restored to the load balancer "
  [lb-name & {:keys [target interval timeout unhealthy-threshold healthy-threshold]}]
  (with-elb-client client
    (.configureHealthCheck client
                           (ConfigureHealthCheckRequest. lb-name
                                                         (HealthCheck. target
                                                                       interval
                                                                       timeout
                                                                       unhealthy-threshold
                                                                       healthy-threshold)))))

(defn create-balancer
  "Create a new load balancer. Returns a string, the DNS name of the load balancer.

  listeners - a seq of maps, each map should have keys :load-balancer-port, :instance-port, :protocol

  availability-zones - a seq of strings

  health-check - (optional) a map, taking the same options as configure-health-check

  "
  [& {:keys [name
             listeners
             availability-zones
             health-check]}]
  (with-elb-client client
    (let [request (CreateLoadBalancerRequest.)]

      (throw-if-not name "name is required")
      (throw-if-not listeners "listeners is required")
      (throw-if-not availability-zones "availability-zones is required")

      (doto request
        (.setLoadBalancerName name)
        (.setListeners (for [listener listeners]
                         (Listener. (-> listener :protocol)
                                    (-> listener :load-balancer-port)
                                    (-> listener :instance-port))))
        (.setAvailabilityZones availability-zones))
      
      (-> client 
          (.createLoadBalancer request)
          (.getDNSName))

      (when health-check
        (apply-map configure-health-check name health-check)))))

(defn describe-balancer [lb-name]
  (with-elb-client client
    (-> client
        (.describeLoadBalancers (DescribeLoadBalancersRequest. [lb-name]))
        (.getLoadBalancerDescriptions)
        (first)
        (bean))))

(defn get-availability-zones [lb-name]
  (-> lb-name
      (describe-balancer)
      :availabilityZones
      (seq)))

(defn add-availability-zone [lb-name new-zone]
  (with-elb-client client
    (println "adding" new-zone "to" lb-name)
    (-> client
        (.enableAvailabilityZonesForLoadBalancer (EnableAvailabilityZonesForLoadBalancerRequest. lb-name [new-zone])))))

(defn ensure-availability-zone [lb-name zone]
  (if (not (contains? (set (get-availability-zones lb-name)) zone))
    (add-availability-zone lb-name zone)
    true))

(defn get-health [lb-name & instance-ids]
  {:post [(do (println "get-health:" %) true)]}
  (let [request (DescribeInstanceHealthRequest. lb-name)]
    (when instance-ids
      (.setInstances request (map #(Instance. %) instance-ids)))
    (with-elb-client client
      (-> client
          (.describeInstanceHealth request)
          (.getInstanceStates)
          (->> (map bean))))))

(defn terminated-instances
  "Returns a seq of instance-ids, all terminated instances attached to the LB"
  [lb-name]
  (->> (get-health lb-name)
       (filter #(= "Instance is in terminated state." (:description %))) ;; there doesn't appear to be a useful, specific error code, so use the description string.
       (map :instanceId)))

(defn healthy? [lb-name & instance-ids]
  (->> instance-ids
       (apply get-health lb-name)
       (every? #(= "InService" (:state %)))))

(defn instance-ids [lb-name]
  (map :instanceId (get-health lb-name)))