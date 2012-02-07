(ns circle.backend.build.resources
  "manages locking & acquiring build resources"
  (:require [circle.redis.semaphore :as semaphore])
  (:require [circle.backend.ec2 :as ec2]))

(def ec2-name "ec2-instance")

(defn init []
  (semaphore/create ec2-name (- ec2/instance-limit 10)))

(defmacro with-resource-lock
  "Acquire and lock resources necessary for the build, such as ec2 instances"
  [& body]
  `(semaphore/with-lock ec2-name {:timeout 0}
     ~@body))