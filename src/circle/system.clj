(ns circle.system
  (:require [circle.backend.build.run :as run])
  (:require [circle.backend.ec2 :as ec2]))

(defn graceful-shutdown
  "Shutdown there are no more running builds."
  []
  (letfn [(shutdown []
            (infof "graceful shutdown watcher, count=%s")
            (when (zero? (count @run/in-progress))
              (let [id (ec2/self-instance-id)]
                (when id
                  (infof "calling self-terminate on %s" id)
                  (ec2/terminate-instances! id)))))]
    (add-watch run/in-progress :shutdown (fn [key in-progress old-state new-state]
                                           (shutdown)))
    (shutdown)))