(ns circle.system
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.ec2 :as ec2])
  (:use [clojure.tools.logging :only (with-logs error infof errorf)]))

(defn graceful-shutdown
  "Shutdown there are no more running builds."
  []
  (letfn [(shutdown []
            (infof "graceful shutdown watcher, count=%s" (count @run/in-progress))
            (when (zero? (count @run/in-progress))
              (let [id (ec2/self-instance-id)]
                (when id
                  (infof "calling self-terminate on %s" id)
                  (ec2/terminate-instances! id)))))]
    (add-watch run/in-progress :shutdown (fn [key in-progress old-state new-state]
                                           (shutdown)))
    (shutdown)))